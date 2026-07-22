package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DownloadVideoTest {
	private val videoId = UUID.fromString("759326a5-03a1-47cf-a91b-64df2b5c05c8")
	private val customerId = UUID.fromString("554d1c71-2de7-4ca5-b854-2286f390617a")
	private val outputKey = ObjectKey.of("customers/$customerId/videos/$videoId/frames.zip")
	private val content = "fake-zip-content".toByteArray()

	@Test
	fun `downloads processed video output`() {
		val storage = RecordingVideoStorage(content)
		val useCase = DownloadVideo(
			authenticatedCustomerProvider = AuthenticatedCustomerProvider { customerId },
			repository = StubVideoQueryRepository(processedVideo()),
			storage = storage,
		)

		val result = useCase.execute(videoId)

		assertEquals("$videoId-frames.zip", result.filename)
		assertContentEquals(content, result.content.use(InputStream::readAllBytes))
		assertEquals(StorageBucket.OUTPUT, storage.downloadedBucket)
		assertEquals(outputKey, storage.downloadedObjectKey)
	}

	@Test
	fun `rejects download while video is not processed`() {
		val storage = RecordingVideoStorage(content)
		val useCase = DownloadVideo(
			authenticatedCustomerProvider = AuthenticatedCustomerProvider { customerId },
			repository = StubVideoQueryRepository(pendingVideo()),
			storage = storage,
		)

		assertFailsWith<VideoNotReadyForDownloadException> { useCase.execute(videoId) }
		assertNull(storage.downloadedObjectKey)
	}

	@Test
	fun `does not expose another customer video`() {
		val useCase = DownloadVideo(
			authenticatedCustomerProvider = AuthenticatedCustomerProvider { UUID.randomUUID() },
			repository = StubVideoQueryRepository(processedVideo()),
			storage = RecordingVideoStorage(content),
		)

		assertFailsWith<VideoNotFoundException> { useCase.execute(videoId) }
	}

	private fun processedVideo(): VideoProcessing = pendingVideo().apply {
		markProcessed(outputKey, updatedAt.plusSeconds(1))
	}

	private fun pendingVideo(): VideoProcessing {
		val receivedAt = Instant.parse("2026-07-22T12:00:00Z")
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

	private class StubVideoQueryRepository(
		private val video: VideoProcessing,
	) : VideoQueryRepository {
		override fun findByIdAndCustomerId(id: UUID, customerId: UUID): VideoProcessing? =
			video.takeIf { it.id == id && it.customerId == customerId }

		override fun findAllByCustomerId(customerId: UUID, pageRequest: VideoPageRequest): VideoPage =
			VideoPage(emptyList(), pageRequest.page, pageRequest.size, 0, 0)
	}

	private class RecordingVideoStorage(private val content: ByteArray) : VideoStorage {
		var downloadedBucket: StorageBucket? = null
		var downloadedObjectKey: ObjectKey? = null

		override fun upload(
			bucket: StorageBucket,
			objectKey: ObjectKey,
			content: InputStream,
			contentLength: Long,
			contentType: String?,
		) = error("Not used")

		override fun exists(bucket: StorageBucket, objectKey: ObjectKey): Boolean = false

		override fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream {
			downloadedBucket = bucket
			downloadedObjectKey = objectKey
			return ByteArrayInputStream(content)
		}
	}
}
