package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.outbox.DispatchOutboxEvents
import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventPublisher
import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventStore
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRequestRegistration
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRequested
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.infrastructure.video.SpringDataVideoProcessingRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class OutboxIntegrationTest(
	@Autowired private val registration: VideoProcessingRequestRegistration,
	@Autowired private val dispatcher: TransactionalOutboxDispatcher,
	@Autowired private val outboxRepository: SpringDataOutboxEventRepository,
	@Autowired private val videoRepository: SpringDataVideoProcessingRepository,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val kafkaContainer: KafkaContainer,
	@Autowired private val outboxStore: OutboxEventStore,
	@Autowired private val transactionManager: PlatformTransactionManager,
) {
	@BeforeEach
	fun cleanDatabase() {
		outboxRepository.deleteAll()
		videoRepository.deleteAll()
	}

	@Test
	fun `publishes pending event once and marks it as published`() {
		val videoId = UUID.fromString("d57d727f-95ba-48f4-957b-26db5994e4c0")
		val customerId = UUID.fromString("0da3ac8a-08e8-48bc-aa07-2c5dbb71b766")
		val eventId = UUID.fromString("13bb8a2a-a18f-43da-8a56-1340c689c151")
		val occurredAt = Instant.parse("2026-07-20T15:00:00Z")
		registration.save(pendingVideo(videoId, customerId, occurredAt), event(eventId, videoId, customerId, occurredAt))

		assertEquals(1, videoRepository.count())
		assertEquals(1, outboxRepository.count())
		assertNull(outboxRepository.findById(eventId).orElseThrow().publishedAt)
		assertEquals(1, dispatcher.dispatch())

		KafkaConsumer<String, String>(consumerProperties()).use { consumer ->
			consumer.subscribe(listOf(VideoProcessingRequested.TOPIC))
			val records = pollRecords(consumer, Duration.ofSeconds(10))
			assertEquals(1, records.size)
			val record = records.single()
			assertEquals(videoId.toString(), record.key())
			val payload = objectMapper.readTree(record.value())
			assertEquals(eventId.toString(), payload.path("eventId").stringValue())
			assertEquals(videoId.toString(), payload.path("videoId").stringValue())
			assertEquals(customerId.toString(), payload.path("customerId").stringValue())

			assertEquals(0, dispatcher.dispatch())
			assertTrue(consumer.poll(Duration.ofSeconds(1)).isEmpty)
		}

		val published = outboxRepository.findById(eventId).orElseThrow()
		assertNotNull(published.publishedAt)
		assertEquals(1, published.attempts)
		assertNull(published.lastError)
	}

	@Test
	fun `rolls back video when outbox event cannot be inserted`() {
		val occurredAt = Instant.parse("2026-07-20T15:00:00Z")
		val existingVideoId = UUID.fromString("1336c71c-2bd9-4500-a323-cd46744b78b6")
		val duplicateEventId = UUID.fromString("54123f88-1263-4976-90f1-117739365d03")
		val customerId = UUID.fromString("96fd8aa6-a0cc-419a-b97c-150bd8efc2e9")
		registration.save(
			pendingVideo(existingVideoId, customerId, occurredAt),
			event(duplicateEventId, existingVideoId, customerId, occurredAt),
		)
		val rolledBackVideoId = UUID.fromString("a722c9c8-52b0-48e8-80f7-775a9e71daee")

		assertFailsWith<DataIntegrityViolationException> {
			registration.save(
				pendingVideo(rolledBackVideoId, customerId, occurredAt),
				event(duplicateEventId, rolledBackVideoId, customerId, occurredAt),
			)
		}

		assertNull(videoRepository.findById(rolledBackVideoId).orElse(null))
		assertEquals(1, videoRepository.count())
		assertEquals(1, outboxRepository.count())
	}

	@Test
	fun `keeps event pending in database when publisher is unavailable`() {
		val occurredAt = Instant.parse("2026-07-20T15:00:00Z")
		val videoId = UUID.fromString("2105ab35-a80b-4b11-98ad-63531b748148")
		val eventId = UUID.fromString("bc6605d9-9305-450a-bef5-d40dc2552460")
		val customerId = UUID.fromString("c25891b2-f2b8-498d-ac6d-b86f367bb77c")
		registration.save(pendingVideo(videoId, customerId, occurredAt), event(eventId, videoId, customerId, occurredAt))
		val dispatcherWithFailure = DispatchOutboxEvents(
			store = outboxStore,
			publisher = OutboxEventPublisher { throw IllegalStateException("Kafka unavailable") },
			clock = Clock.fixed(occurredAt, ZoneOffset.UTC),
			retryDelay = Duration.ofSeconds(5),
		)

		TransactionTemplate(transactionManager).executeWithoutResult {
			assertEquals(1, dispatcherWithFailure.execute(10))
		}

		val pending = outboxRepository.findById(eventId).orElseThrow()
		assertNull(pending.publishedAt)
		assertEquals(1, pending.attempts)
		assertEquals("Kafka unavailable", pending.lastError)
		assertEquals(occurredAt.plusSeconds(5), pending.nextAttemptAt)
	}

	private fun pendingVideo(videoId: UUID, customerId: UUID, occurredAt: Instant): VideoProcessing =
		VideoProcessing.receive(
			id = videoId,
			customerId = customerId,
			originalFilename = OriginalFilename.of("sample.mp4"),
			receivedAt = occurredAt,
		).apply {
			markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input"), occurredAt)
			markPendingProcessing(occurredAt)
		}

	private fun event(
		eventId: UUID,
		videoId: UUID,
		customerId: UUID,
		occurredAt: Instant,
	) = VideoProcessingRequested(
		eventId = eventId,
		occurredAt = occurredAt,
		videoId = videoId,
		customerId = customerId,
		originalFilename = "sample.mp4",
		inputObjectKey = "customers/$customerId/videos/$videoId/input",
	)

	private fun consumerProperties(): Map<String, Any> = mapOf(
		ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
		ConsumerConfig.GROUP_ID_CONFIG to "outbox-integration-test-${UUID.randomUUID()}",
		ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
		ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
		ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
		ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
	)

	private fun pollRecords(
		consumer: KafkaConsumer<String, String>,
		timeout: Duration,
	): List<ConsumerRecord<String, String>> {
		val deadline = System.nanoTime() + timeout.toNanos()
		val records = mutableListOf<ConsumerRecord<String, String>>()
		while (records.isEmpty() && System.nanoTime() < deadline) {
			consumer.poll(Duration.ofMillis(500)).forEach(records::add)
		}
		return records
	}
}
