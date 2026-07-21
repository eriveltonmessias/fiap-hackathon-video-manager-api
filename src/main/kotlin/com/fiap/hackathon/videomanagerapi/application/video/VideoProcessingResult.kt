package com.fiap.hackathon.videomanagerapi.application.video

import java.time.Instant
import java.util.UUID

sealed interface VideoProcessingResult {
	val eventId: UUID
	val eventType: String
	val occurredAt: Instant
	val videoId: UUID
}

data class VideoProcessed(
	override val eventId: UUID,
	override val eventType: String,
	override val occurredAt: Instant,
	override val videoId: UUID,
	val outputObjectKey: String,
) : VideoProcessingResult {
	init {
		require(eventType == EVENT_TYPE) { "eventType must be $EVENT_TYPE" }
	}

	companion object {
		const val EVENT_TYPE = "VideoProcessed"
		const val TOPIC = "video.processing.completed"
	}
}

data class VideoProcessingFailed(
	override val eventId: UUID,
	override val eventType: String,
	override val occurredAt: Instant,
	override val videoId: UUID,
	val failureReason: String,
) : VideoProcessingResult {
	init {
		require(eventType == EVENT_TYPE) { "eventType must be $EVENT_TYPE" }
	}

	companion object {
		const val EVENT_TYPE = "VideoProcessingFailed"
		const val TOPIC = "video.processing.failed"
	}
}
