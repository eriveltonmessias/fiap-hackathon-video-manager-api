package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.StorageBucket
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(properties = ["app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class VideoDownloadIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
	@Autowired private val repository: VideoProcessingRepository,
	@Autowired private val springDataRepository: SpringDataVideoProcessingRepository,
	@Autowired private val storage: VideoStorage,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `downloads processed output only for its owner`() {
		val customerId = UUID.randomUUID()
		val videoId = UUID.randomUUID()
		val outputKey = ObjectKey.of("customers/$customerId/videos/$videoId/frames.zip")
		val zipContent = "fake-zip-content".toByteArray()
		repository.save(processedVideo(videoId, customerId, outputKey))
		storage.upload(
			bucket = StorageBucket.OUTPUT,
			objectKey = outputKey,
			content = ByteArrayInputStream(zipContent),
			contentLength = zipContent.size.toLong(),
			contentType = "application/zip",
		)

		mockMvc.get("/videos/{videoId}/download", videoId) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}")
		}.andExpect {
			status { isOk() }
			header { string(HttpHeaders.CONTENT_TYPE, "application/zip") }
			header { string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$videoId-frames.zip\"") }
			content { bytes(zipContent) }
		}
	}

	@Test
	fun `returns conflict while processing is pending`() {
		val customerId = UUID.randomUUID()
		val video = pendingVideo(UUID.randomUUID(), customerId)
		repository.save(video)

		mockMvc.get("/videos/{videoId}/download", video.id) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(customerId)}")
		}.andExpect {
			status { isConflict() }
			jsonPath("$.status") { value(409) }
			jsonPath("$.message") { value("Video result is not available for status PENDING_PROCESSING") }
		}
	}

	@Test
	fun `does not reveal another customer result`() {
		val ownerId = UUID.randomUUID()
		val requesterId = UUID.randomUUID()
		val videoId = UUID.randomUUID()
		val outputKey = ObjectKey.of("customers/$ownerId/videos/$videoId/frames.zip")
		repository.save(processedVideo(videoId, ownerId, outputKey))

		mockMvc.get("/videos/{videoId}/download", videoId) {
			header(HttpHeaders.AUTHORIZATION, "Bearer ${token(requesterId)}")
		}.andExpect {
			status { isNotFound() }
			jsonPath("$.message") { value("Video not found") }
		}
	}

	private fun processedVideo(videoId: UUID, customerId: UUID, outputKey: ObjectKey): VideoProcessing =
		pendingVideo(videoId, customerId).apply {
			markProcessed(outputKey, updatedAt.plusSeconds(1))
		}

	private fun pendingVideo(videoId: UUID, customerId: UUID): VideoProcessing {
		val receivedAt = Instant.now().minusSeconds(5)
		return VideoProcessing.receive(
			id = videoId,
			customerId = customerId,
			originalFilename = OriginalFilename.of("lesson.mp4"),
			receivedAt = receivedAt,
		).apply {
			markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input.mp4"), receivedAt.plusSeconds(1))
			markPendingProcessing(receivedAt.plusSeconds(2))
		}
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
