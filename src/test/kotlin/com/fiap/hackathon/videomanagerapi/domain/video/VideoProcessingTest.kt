package com.fiap.hackathon.videomanagerapi.domain.video

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoProcessingTest {
	private val receivedAt = Instant.parse("2026-01-01T10:00:00Z")
	private val videoId = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
	private val customerId = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")

	@Test
	fun `receives a new video`() {
		val video = newVideo()

		assertEquals(videoId, video.id)
		assertEquals(customerId, video.customerId)
		assertEquals("lesson.mp4", video.originalFilename.value)
		assertEquals(VideoStatus.RECEIVED, video.status)
		assertNull(video.inputObjectKey)
		assertNull(video.outputObjectKey)
		assertNull(video.failureReason)
		assertEquals(receivedAt, video.createdAt)
		assertEquals(receivedAt, video.updatedAt)
		assertFalse(video.isTerminal())
	}

	@Test
	fun `completes the valid processing lifecycle`() {
		val video = newVideo()
		val inputKey = ObjectKey.of("input/$customerId/$videoId/lesson.mp4")
		val outputKey = ObjectKey.of("output/$customerId/$videoId/frames.zip")

		video.markStored(inputKey, receivedAt.plusSeconds(1))
		assertEquals(VideoStatus.STORED, video.status)
		assertEquals(inputKey, video.inputObjectKey)

		video.markPendingProcessing(receivedAt.plusSeconds(2))
		assertEquals(VideoStatus.PENDING_PROCESSING, video.status)

		video.markProcessing(receivedAt.plusSeconds(3))
		assertEquals(VideoStatus.PROCESSING, video.status)

		video.markProcessed(outputKey, receivedAt.plusSeconds(4))
		assertEquals(VideoStatus.PROCESSED, video.status)
		assertEquals(outputKey, video.outputObjectKey)
		assertEquals(receivedAt.plusSeconds(4), video.updatedAt)
		assertTrue(video.isTerminal())
	}

	@Test
	fun `marks a non terminal video as failed`() {
		val video = newVideo()
		val reason = FailureReason.of("Storage unavailable")

		video.markFailed(reason, receivedAt.plusSeconds(1))

		assertEquals(VideoStatus.FAILED, video.status)
		assertEquals(reason, video.failureReason)
		assertTrue(video.isTerminal())
		assertFailsWith<IllegalStateException> {
			video.markFailed(FailureReason.of("Another error"), receivedAt.plusSeconds(2))
		}
	}

	@Test
	fun `rejects an invalid state transition without changing the aggregate`() {
		val video = newVideo()

		assertFailsWith<IllegalStateException> {
			video.markProcessing(receivedAt.plusSeconds(1))
		}

		assertEquals(VideoStatus.RECEIVED, video.status)
		assertEquals(receivedAt, video.updatedAt)
	}

	@Test
	fun `rejects an older timestamp without partially changing state`() {
		val video = newVideo()
		val inputKey = ObjectKey.of("input/video.mp4")

		assertFailsWith<IllegalArgumentException> {
			video.markStored(inputKey, receivedAt.minusSeconds(1))
		}

		assertEquals(VideoStatus.RECEIVED, video.status)
		assertNull(video.inputObjectKey)
		assertEquals(receivedAt, video.updatedAt)
	}

	@Test
	fun `rejects an inconsistent restored snapshot`() {
		assertFailsWith<IllegalArgumentException> {
			VideoProcessing.restore(
				id = videoId,
				customerId = customerId,
				originalFilename = OriginalFilename.of("lesson.mp4"),
				status = VideoStatus.PROCESSED,
				inputObjectKey = ObjectKey.of("input/lesson.mp4"),
				outputObjectKey = null,
				failureReason = null,
				createdAt = receivedAt,
				updatedAt = receivedAt.plusSeconds(10),
			)
		}
	}

	@Test
	fun `rejects an unsafe original filename`() {
		assertFailsWith<IllegalArgumentException> {
			OriginalFilename.of("../lesson.mp4")
		}
	}

	private fun newVideo(): VideoProcessing = VideoProcessing.receive(
		id = videoId,
		customerId = customerId,
		originalFilename = OriginalFilename.of(" lesson.mp4 "),
		receivedAt = receivedAt,
	)
}
