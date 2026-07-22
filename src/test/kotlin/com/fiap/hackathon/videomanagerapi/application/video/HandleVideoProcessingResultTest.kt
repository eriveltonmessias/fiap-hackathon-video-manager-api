package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class HandleVideoProcessingResultTest {
	private val videoId = UUID.fromString("83bece0c-8e9f-477e-a816-f32e910c87f0")
	private val customerId = UUID.fromString("74f398ff-7ed2-4fb9-9407-d122477d7244")
	private val occurredAt = Instant.parse("2026-07-20T18:00:00Z")
	private val repository = InMemoryVideoProcessingRepository(pendingVideo())
	private val registry = InMemoryProcessedVideoEventRegistry()
	private val handler = HandleVideoProcessingResult(repository, registry)

	@Test
	fun `stores a successful processing result`() {
		val event = VideoProcessed(
			eventId = UUID.fromString("69d5d9ba-46bd-4c72-a465-b491369aaf8c"),
			eventType = VideoProcessed.EVENT_TYPE,
			occurredAt = occurredAt,
			videoId = videoId,
			outputObjectKey = "customers/$customerId/videos/$videoId/frames.zip",
		)

		assertEquals(HandlingResult.PROCESSED, handler.handle(event))
		assertEquals(VideoStatus.PROCESSED, repository.findById(videoId)?.status)
		assertEquals(event.outputObjectKey, repository.findById(videoId)?.outputObjectKey?.value)
	}

	@Test
	fun `stores a failed processing result`() {
		val event = VideoProcessingFailed(
			eventId = UUID.fromString("c950e605-f282-4e50-b5b0-d3466292d23a"),
			eventType = VideoProcessingFailed.EVENT_TYPE,
			occurredAt = occurredAt,
			videoId = videoId,
			failureReason = "Unsupported video codec",
		)

		assertEquals(HandlingResult.PROCESSED, handler.handle(event))
		assertEquals(VideoStatus.FAILED, repository.findById(videoId)?.status)
		assertEquals(event.failureReason, repository.findById(videoId)?.failureReason?.value)
	}

	@Test
	fun `ignores an event already registered`() {
		val event = VideoProcessed(
			eventId = UUID.fromString("e467810d-e6db-42ab-92ac-cb4418fc7c0c"),
			eventType = VideoProcessed.EVENT_TYPE,
			occurredAt = occurredAt,
			videoId = videoId,
			outputObjectKey = "customers/$customerId/videos/$videoId/frames.zip",
		)

		assertEquals(HandlingResult.PROCESSED, handler.handle(event))
		assertEquals(HandlingResult.ALREADY_PROCESSED, handler.handle(event))
		assertEquals(1, registry.size)
		assertEquals(VideoStatus.PROCESSED, repository.findById(videoId)?.status)
	}

	private fun pendingVideo(): VideoProcessing = VideoProcessing.receive(
		id = videoId,
		customerId = customerId,
		originalFilename = OriginalFilename.of("lesson.mp4"),
		receivedAt = occurredAt.minusSeconds(10),
	).apply {
		markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input.mp4"), occurredAt.minusSeconds(9))
		markPendingProcessing(occurredAt.minusSeconds(8))
	}

	private class InMemoryVideoProcessingRepository(initial: VideoProcessing) : VideoProcessingRepository {
		private val videos = mutableMapOf(initial.id to initial)

		override fun save(videoProcessing: VideoProcessing): VideoProcessing = videoProcessing.also {
			videos[it.id] = it
		}

		override fun findById(id: UUID): VideoProcessing? = videos[id]
		override fun findByIdForUpdate(id: UUID): VideoProcessing? = videos[id]
	}

	private class InMemoryProcessedVideoEventRegistry : ProcessedVideoEventRegistry {
		private val eventIds = mutableSetOf<UUID>()
		val size: Int get() = eventIds.size

		override fun register(eventId: UUID, videoId: UUID, eventType: String, processedAt: Instant): Boolean =
			eventIds.add(eventId)
	}
}
