package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.StorageBucket
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
	properties = [
		"app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes",
		"app.video-upload.maximum-file-size=10B",
	],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class VideoUploadIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val repository: VideoProcessingRepository,
	@Autowired private val springDataRepository: SpringDataVideoProcessingRepository,
	@Autowired private val videoStorage: VideoStorage,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `authenticated customer uploads video`() {
		val customerId = UUID.fromString("ec861406-99ad-438d-acda-031379fe2180")
		val response = mockMvc.perform(
			multipart("/videos")
				.file(videoFile())
				.header("Authorization", "Bearer ${token(customerId)}"),
		).andExpect(status().isAccepted)
			.andExpect(jsonPath("$.status").value("STORED"))
			.andReturn().response.contentAsString
		val videoId = UUID.fromString(objectMapper.readTree(response).path("videoId").stringValue())

		val saved = assertNotNull(repository.findById(videoId))
		assertEquals(customerId, saved.customerId)
		assertEquals(VideoStatus.STORED, saved.status)
		assertEquals("sample.mp4", saved.originalFilename.value)
		assertTrue(videoStorage.exists(StorageBucket.INPUT, assertNotNull(saved.inputObjectKey)))
	}

	@Test
	fun `rejects unsupported file`() {
		val file = MockMultipartFile("file", "notes.txt", "text/plain", "text".toByteArray())

		mockMvc.perform(
			multipart("/videos")
				.file(file)
				.header("Authorization", "Bearer ${token(UUID.randomUUID())}"),
		).andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.status").value(400))
		assertEquals(0, springDataRepository.count())
	}

	@Test
	fun `rejects video larger than configured limit`() {
		val file = MockMultipartFile("file", "sample.mp4", "video/mp4", ByteArray(11))

		mockMvc.perform(
			multipart("/videos")
				.file(file)
				.header("Authorization", "Bearer ${token(UUID.randomUUID())}"),
		).andExpect(status().isContentTooLarge)
			.andExpect(jsonPath("$.status").value(413))
		assertEquals(0, springDataRepository.count())
	}

	@Test
	fun `rejects upload without token`() {
		mockMvc.perform(multipart("/videos").file(videoFile()))
			.andExpect(status().isUnauthorized)
		assertEquals(0, springDataRepository.count())
	}

	private fun videoFile() = MockMultipartFile("file", "sample.mp4", "video/mp4", "video".toByteArray())

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
