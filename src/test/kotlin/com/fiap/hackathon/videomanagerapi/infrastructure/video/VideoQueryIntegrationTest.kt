package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(properties = ["app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class VideoQueryIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
	@Autowired private val repository: VideoProcessingRepository,
	@Autowired private val springDataRepository: SpringDataVideoProcessingRepository,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `lists only authenticated customer videos with pagination`() {
		val customerId = UUID.fromString("ca312036-e4e3-4019-aa2b-0ec92a720ace")
		val otherCustomerId = UUID.fromString("3921c18c-c9cd-4357-8fa5-f7a448725cc6")
		val oldestId = UUID.fromString("61307a0f-8f9c-4549-bdd5-c01d8b3db6df")
		val middleId = UUID.fromString("a8b02607-0ed0-4bea-8e78-65f5ba6c22b7")
		val newestId = UUID.fromString("36a512d7-4606-4a09-8aa9-f421bb28af71")
		repository.save(storedVideo(oldestId, customerId, "oldest.mp4", Instant.parse("2026-07-20T10:00:00Z")))
		repository.save(storedVideo(middleId, customerId, "middle.mp4", Instant.parse("2026-07-20T11:00:00Z")))
		repository.save(storedVideo(newestId, customerId, "newest.mp4", Instant.parse("2026-07-20T12:00:00Z")))
		repository.save(storedVideo(UUID.randomUUID(), otherCustomerId, "private.mp4", Instant.parse("2026-07-20T13:00:00Z")))

		mockMvc.get("/videos") {
			param("page", "0")
			param("size", "2")
			header("Authorization", "Bearer ${token(customerId)}")
		}.andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(2) }
			jsonPath("$.content[0].videoId") { value(newestId.toString()) }
			jsonPath("$.content[1].videoId") { value(middleId.toString()) }
			jsonPath("$.content[0].inputObjectKey") { doesNotExist() }
			jsonPath("$.content[0].outputObjectKey") { doesNotExist() }
			jsonPath("$.page") { value(0) }
			jsonPath("$.size") { value(2) }
			jsonPath("$.totalElements") { value(3) }
			jsonPath("$.totalPages") { value(2) }
		}
	}

	@Test
	fun `returns authenticated customer video without storage keys`() {
		val customerId = UUID.fromString("7f2b72f5-c8f3-4fab-9fcb-0417566bca29")
		val videoId = UUID.fromString("a3447b09-894e-421a-8921-df55f07d6809")
		repository.save(storedVideo(videoId, customerId, "sample.mp4", Instant.parse("2026-07-20T12:00:00Z")))

		mockMvc.get("/videos/{videoId}", videoId) {
			header("Authorization", "Bearer ${token(customerId)}")
		}.andExpect {
			status { isOk() }
			jsonPath("$.videoId") { value(videoId.toString()) }
			jsonPath("$.originalFilename") { value("sample.mp4") }
			jsonPath("$.status") { value("STORED") }
			jsonPath("$.inputObjectKey") { doesNotExist() }
			jsonPath("$.outputObjectKey") { doesNotExist() }
		}
	}

	@Test
	fun `does not reveal whether video belongs to another customer`() {
		val ownerId = UUID.fromString("45166587-e1ec-4621-af7f-4ff8faef1968")
		val requesterId = UUID.fromString("2cb2c1a7-63b6-42da-83a4-183438e4b58a")
		val privateVideoId = UUID.fromString("bbd990dd-3474-4248-8c7b-ed4ba237991c")
		repository.save(storedVideo(privateVideoId, ownerId, "private.mp4", Instant.parse("2026-07-20T12:00:00Z")))
		val missingVideoId = UUID.fromString("33713a16-fd23-467a-96b5-2aacfc252741")
		val token = token(requesterId)

		listOf(privateVideoId, missingVideoId).forEach { videoId ->
			mockMvc.get("/videos/{videoId}", videoId) {
				header("Authorization", "Bearer $token")
			}.andExpect {
				status { isNotFound() }
				jsonPath("$.message") { value("Video not found") }
			}
		}
	}

	@Test
	fun `rejects invalid pagination`() {
		mockMvc.get("/videos") {
			param("page", "-1")
			param("size", "101")
			header("Authorization", "Bearer ${token(UUID.randomUUID())}")
		}.andExpect {
			status { isBadRequest() }
			jsonPath("$.status") { value(400) }
		}
	}

	private fun storedVideo(
		videoId: UUID,
		customerId: UUID,
		filename: String,
		createdAt: Instant,
	): VideoProcessing = VideoProcessing.receive(
		id = videoId,
		customerId = customerId,
		originalFilename = OriginalFilename.of(filename),
		receivedAt = createdAt,
	).apply {
		markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input"), createdAt)
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
