package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.FailureReason
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import java.time.Instant
import java.util.UUID

fun interface ProcessedVideoEventRegistry {
	fun register(eventId: UUID, videoId: UUID, eventType: String, processedAt: Instant): Boolean
}

class VideoProcessingNotFoundException(videoId: UUID) :
	RuntimeException("Video processing $videoId was not found")

class HandleVideoProcessingResult(
	private val repository: VideoProcessingRepository,
	private val eventRegistry: ProcessedVideoEventRegistry,
) {
	fun handle(event: VideoProcessingResult): HandlingResult {
		val video = repository.findByIdForUpdate(event.videoId)
			?: throw VideoProcessingNotFoundException(event.videoId)

		if (!eventRegistry.register(event.eventId, event.videoId, event.eventType, event.occurredAt)) {
			return HandlingResult.ALREADY_PROCESSED
		}

		when (event) {
			is VideoProcessed -> video.markProcessed(ObjectKey.of(event.outputObjectKey), event.occurredAt)
			is VideoProcessingFailed -> video.markFailed(FailureReason.of(event.failureReason), event.occurredAt)
		}
		repository.save(video)
		return HandlingResult.PROCESSED
	}
}

enum class HandlingResult {
	PROCESSED,
	ALREADY_PROCESSED,
}
