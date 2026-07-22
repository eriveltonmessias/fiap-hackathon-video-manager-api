package com.fiap.hackathon.videomanagerapi.infrastructure.flow

import com.fiap.hackathon.videomanagerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.StorageBucket
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRequested
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import com.fiap.hackathon.videomanagerapi.infrastructure.notification.SpringDataNotificationFailureRepository
import com.fiap.hackathon.videomanagerapi.infrastructure.outbox.SpringDataOutboxEventRepository
import com.fiap.hackathon.videomanagerapi.infrastructure.outbox.TransactionalOutboxDispatcher
import com.fiap.hackathon.videomanagerapi.infrastructure.processing.SpringDataProcessedVideoEventRepository
import com.fiap.hackathon.videomanagerapi.infrastructure.video.SpringDataVideoProcessingRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
	properties = [
		"app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes",
		"app.processing-results.retry-interval=50ms",
		"app.processing-results.max-retries=1",
	],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class VideoProcessingFlowIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val videoRepository: VideoProcessingRepository,
	@Autowired private val springDataVideoRepository: SpringDataVideoProcessingRepository,
	@Autowired private val outboxRepository: SpringDataOutboxEventRepository,
	@Autowired private val processedEventRepository: SpringDataProcessedVideoEventRepository,
	@Autowired private val notificationFailureRepository: SpringDataNotificationFailureRepository,
	@Autowired private val storage: VideoStorage,
	@Autowired private val outboxDispatcher: TransactionalOutboxDispatcher,
	@Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
	@Autowired private val kafkaContainer: KafkaContainer,
	@Autowired private val meterRegistry: MeterRegistry,
) {
	@BeforeEach
	fun cleanDatabase() {
		notificationFailureRepository.deleteAll()
		processedEventRepository.deleteAll()
		outboxRepository.deleteAll()
		springDataVideoRepository.deleteAll()
	}

	@Test
	fun `processes a video end to end and ignores the repeated completion event`() {
		val customerId = UUID.fromString("17805284-ec77-4b01-9d55-e377d33d09f3")
		val uploadContent = "integration-video".toByteArray()
		val uploadResponse = mockMvc.perform(
			multipart("/videos")
				.file(MockMultipartFile("file", "lesson.mp4", "video/mp4", uploadContent))
				.header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}"),
		).andExpect(status().isAccepted)
			.andExpect(jsonPath("$.status").value("PENDING_PROCESSING"))
			.andReturn().response.contentAsString
		val videoId = UUID.fromString(objectMapper.readTree(uploadResponse).path("videoId").stringValue())

		val pendingVideo = assertNotNull(videoRepository.findById(videoId))
		assertEquals(customerId, pendingVideo.customerId)
		assertEquals(VideoStatus.PENDING_PROCESSING, pendingVideo.status)
		assertTrue(storage.exists(StorageBucket.INPUT, assertNotNull(pendingVideo.inputObjectKey)))

		KafkaConsumer<String, String>(consumerProperties()).use { consumer ->
			consumer.subscribe(listOf(VideoProcessingRequested.TOPIC))
			outboxDispatcher.dispatch()
			val requestRecord = assertNotNull(pollRecord(consumer, Duration.ofSeconds(15)))
			assertEquals(videoId.toString(), requestRecord.key())
			val requestEvent = objectMapper.readTree(requestRecord.value())
			assertEquals(videoId.toString(), requestEvent.path("videoId").stringValue())
			assertEquals(customerId.toString(), requestEvent.path("customerId").stringValue())
			assertNotNull(outboxRepository.findAll().single().publishedAt)
		}

		val outputKey = ObjectKey.of("customers/$customerId/videos/$videoId/frames.zip")
		val outputContent = "integration-zip".toByteArray()
		storage.upload(
			StorageBucket.OUTPUT,
			outputKey,
			ByteArrayInputStream(outputContent),
			outputContent.size.toLong(),
			"application/zip",
		)
		val completionEvent = VideoProcessed(
			eventId = UUID.fromString("71d360c1-e247-48b4-9829-f6c098de78de"),
			eventType = VideoProcessed.EVENT_TYPE,
			occurredAt = Instant.now(),
			videoId = videoId,
			outputObjectKey = outputKey.value,
		)
		val completionPayload = objectMapper.writeValueAsString(completionEvent)
		kafkaTemplate.send(VideoProcessed.TOPIC, videoId.toString(), completionPayload).get()
		kafkaTemplate.send(VideoProcessed.TOPIC, videoId.toString(), completionPayload).get()

		val processedVideo = awaitValue {
			videoRepository.findById(videoId)?.takeIf { it.status == VideoStatus.PROCESSED }
		}
		assertEquals(outputKey, processedVideo.outputObjectKey)
		awaitCondition {
			meterRegistry.find("video.manager.processing.results")
				.tags("outcome", "already_processed")
				.counter()?.count() == 1.0
		}
		assertEquals(1, processedEventRepository.count())

		mockMvc.get("/videos/{videoId}", videoId) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}")
		}.andExpect {
			status { isOk() }
			jsonPath("$.status") { value("PROCESSED") }
		}
		mockMvc.get("/videos/{videoId}/download", videoId) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}")
		}.andExpect {
			status { isOk() }
			content { bytes(outputContent) }
		}
		mockMvc.get("/videos/{videoId}", videoId) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(UUID.randomUUID())}")
		}.andExpect {
			status { isNotFound() }
			jsonPath("$.message") { value("Video not found") }
		}
	}

	@Test
	fun `rejects unauthenticated upload without side effects`() {
		mockMvc.perform(
			multipart("/videos").file(MockMultipartFile("file", "lesson.mp4", "video/mp4", "video".toByteArray())),
		).andExpect(status().isUnauthorized)

		assertEquals(0, springDataVideoRepository.count())
		assertEquals(0, outboxRepository.count())
	}

	@Test
	fun `rejects invalid file without writing database or MinIO`() {
		val customerId = UUID.randomUUID()
		mockMvc.perform(
			multipart("/videos")
				.file(MockMultipartFile("file", "notes.txt", "text/plain", "invalid".toByteArray()))
				.header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}"),
		).andExpect(status().isBadRequest)

		assertEquals(0, springDataVideoRepository.count())
		assertEquals(0, outboxRepository.count())
	}

	private fun consumerProperties(): Map<String, Any> = mapOf(
		ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
		ConsumerConfig.GROUP_ID_CONFIG to "video-flow-${UUID.randomUUID()}",
		ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
		ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
		ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
		ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
	)

	private fun pollRecord(
		consumer: KafkaConsumer<String, String>,
		timeout: Duration,
	): ConsumerRecord<String, String>? = pollUntil(timeout) {
		consumer.poll(Duration.ofMillis(250)).firstOrNull()
	}

	private fun awaitCondition(condition: () -> Boolean) {
		assertTrue(pollUntil(Duration.ofSeconds(15)) { condition().takeIf { it } } == true)
	}

	private fun <T : Any> awaitValue(action: () -> T?): T =
		assertNotNull(pollUntil(Duration.ofSeconds(15), action))

	private fun <T> pollUntil(timeout: Duration, action: () -> T?): T? {
		val deadline = System.nanoTime() + timeout.toNanos()
		while (System.nanoTime() < deadline) {
			action()?.let { return it }
			Thread.sleep(100)
		}
		return null
	}

	private fun token(customerId: UUID): String {
		val key = SecretKeySpec(JWT_SECRET.toByteArray(), "HmacSHA256")
		val encoder = NimbusJwtEncoder(ImmutableSecret(key))
		val issuedAt = Instant.now()
		val claims = JwtClaimsSet.builder()
			.issuer("customer-auth-api")
			.subject(customerId.toString())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plusSeconds(3600))
			.claim("customer_id", customerId.toString())
			.build()
		return encoder.encode(
			JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
		).tokenValue
	}

	private companion object {
		const val JWT_SECRET = "test-jwt-secret-with-at-least-thirty-two-bytes"
	}
}
