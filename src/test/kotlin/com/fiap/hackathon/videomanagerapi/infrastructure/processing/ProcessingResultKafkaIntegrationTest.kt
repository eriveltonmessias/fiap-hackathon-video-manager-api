package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import com.fiap.hackathon.videomanagerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingFailed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import com.fiap.hackathon.videomanagerapi.infrastructure.video.SpringDataVideoProcessingRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
	properties = [
		"app.processing-results.retry-interval=50ms",
		"app.processing-results.max-retries=1",
	],
)
@Import(TestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class ProcessingResultKafkaIntegrationTest(
	@Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val repository: VideoProcessingRepository,
	@Autowired private val springDataRepository: SpringDataVideoProcessingRepository,
	@Autowired private val processedEventRepository: SpringDataProcessedVideoEventRepository,
	@Autowired private val kafkaContainer: KafkaContainer,
	@Autowired private val listener: ProcessingResultKafkaListener,
) {
	@BeforeEach
	fun cleanDatabase() {
		processedEventRepository.deleteAll()
		springDataRepository.deleteAll()
	}

	@Test
	fun `consumes success and stores the output object key`() {
		val video = savePendingVideo()
		val event = VideoProcessed(
			eventId = UUID.randomUUID(),
			eventType = VideoProcessed.EVENT_TYPE,
			occurredAt = video.updatedAt.plusSeconds(1),
			videoId = video.id,
			outputObjectKey = "customers/${video.customerId}/videos/${video.id}/frames.zip",
		)
		val payload = objectMapper.writeValueAsString(event)

		kafkaTemplate.send(VideoProcessed.TOPIC, video.id.toString(), payload).get()

		val processed = awaitVideo(video.id, VideoStatus.PROCESSED)
		assertEquals(event.outputObjectKey, processed.outputObjectKey?.value)
		awaitCondition { processedEventRepository.count() == 1L }
	}

	@Test
	fun `handles a repeated event idempotently in separate transactions`() {
		val video = savePendingVideo()
		val event = VideoProcessed(
			eventId = UUID.randomUUID(),
			eventType = VideoProcessed.EVENT_TYPE,
			occurredAt = video.updatedAt.plusSeconds(1),
			videoId = video.id,
			outputObjectKey = "customers/${video.customerId}/videos/${video.id}/frames.zip",
		)
		val payload = objectMapper.writeValueAsString(event)

		listener.consumeProcessed(payload)
		listener.consumeProcessed(payload)

		val processed = repository.findById(video.id)
		assertEquals(VideoStatus.PROCESSED, processed?.status)
		assertEquals(event.outputObjectKey, processed?.outputObjectKey?.value)
		assertEquals(1, processedEventRepository.count())
	}

	@Test
	fun `consumes failure and stores its safe reason`() {
		val video = savePendingVideo()
		val event = VideoProcessingFailed(
			eventId = UUID.randomUUID(),
			eventType = VideoProcessingFailed.EVENT_TYPE,
			occurredAt = video.updatedAt.plusSeconds(1),
			videoId = video.id,
			failureReason = "Unsupported video codec",
		)

		kafkaTemplate.send(
			VideoProcessingFailed.TOPIC,
			video.id.toString(),
			objectMapper.writeValueAsString(event),
		).get()

		val failed = awaitVideo(video.id, VideoStatus.FAILED)
		assertEquals(event.failureReason, failed.failureReason?.value)
		assertEquals(1, processedEventRepository.count())
	}

	@Test
	fun `publishes an invalid event to the source topic dlq after retries`() {
		val invalidPayload = "{\"eventType\":\"VideoProcessed\",\"videoId\":\"invalid\"}"
		val dlqTopic = "${VideoProcessed.TOPIC}.dlq"

		KafkaConsumer<String, String>(consumerProperties()).use { consumer ->
			consumer.subscribe(listOf(dlqTopic))
			kafkaTemplate.send(VideoProcessed.TOPIC, "invalid", invalidPayload).get()

			val record = pollUntil(Duration.ofSeconds(15)) {
				consumer.poll(Duration.ofMillis(250)).firstOrNull { it.value() == invalidPayload }
			}
			assertNotNull(record)
			assertEquals(dlqTopic, record.topic())
			assertEquals(invalidPayload, record.value())
		}
	}

	private fun savePendingVideo(): VideoProcessing {
		val now = Instant.now().minusSeconds(5)
		val video = VideoProcessing.receive(
			id = UUID.randomUUID(),
			customerId = UUID.randomUUID(),
			originalFilename = OriginalFilename.of("lesson.mp4"),
			receivedAt = now,
		).apply {
			markStored(ObjectKey.of("customers/$customerId/videos/$id/input.mp4"), now.plusSeconds(1))
			markPendingProcessing(now.plusSeconds(2))
		}
		return repository.save(video)
	}

	private fun awaitVideo(videoId: UUID, status: VideoStatus): VideoProcessing {
		val video = pollUntil(Duration.ofSeconds(15)) {
			repository.findById(videoId)?.takeIf { it.status == status }
		}
		return assertNotNull(video)
	}

	private fun awaitCondition(condition: () -> Boolean) {
		assertTrue(pollUntil(Duration.ofSeconds(10)) { condition().takeIf { it } } == true)
	}

	private fun <T> pollUntil(timeout: Duration, action: () -> T?): T? {
		val deadline = System.nanoTime() + timeout.toNanos()
		while (System.nanoTime() < deadline) {
			action()?.let { return it }
			Thread.sleep(100)
		}
		return null
	}

	private fun consumerProperties(): Map<String, Any> = mapOf(
		ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
		ConsumerConfig.GROUP_ID_CONFIG to "processing-results-dlq-test-${UUID.randomUUID()}",
		ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
		ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
		ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
	)
}
