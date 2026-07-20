package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class JpaVideoProcessingRepositoryIntegrationTest(
	@Autowired private val videoProcessingRepository: VideoProcessingRepository,
	@Autowired private val springDataRepository: SpringDataVideoProcessingRepository,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `persists and restores the complete aggregate in PostgreSQL`() {
		val receivedAt = Instant.parse("2026-07-20T10:00:00Z")
		val video = VideoProcessing.receive(
			id = UUID.fromString("8e711dbe-44fa-4de2-9f9b-906fc0329153"),
			customerId = UUID.fromString("6392514e-78a3-43da-92ef-89c62fdbbafb"),
			originalFilename = OriginalFilename.of("architecture-review.mp4"),
			receivedAt = receivedAt,
		).apply {
			markStored(ObjectKey.of("videos/input/architecture-review.mp4"), receivedAt.plusSeconds(1))
			markPendingProcessing(receivedAt.plusSeconds(2))
			markProcessing(receivedAt.plusSeconds(3))
			markProcessed(ObjectKey.of("videos/output/architecture-review.zip"), receivedAt.plusSeconds(4))
		}

		videoProcessingRepository.save(video)

		val restored = assertNotNull(videoProcessingRepository.findById(video.id))
		assertEquals(video.id, restored.id)
		assertEquals(video.customerId, restored.customerId)
		assertEquals(video.originalFilename, restored.originalFilename)
		assertEquals(VideoStatus.PROCESSED, restored.status)
		assertEquals(video.inputObjectKey, restored.inputObjectKey)
		assertEquals(video.outputObjectKey, restored.outputObjectKey)
		assertEquals(null, restored.failureReason)
		assertEquals(video.createdAt, restored.createdAt)
		assertEquals(video.updatedAt, restored.updatedAt)
	}

	@Test
	fun `returns null when video does not exist`() {
		assertEquals(null, videoProcessingRepository.findById(UUID.randomUUID()))
	}
}
