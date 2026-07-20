package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.time.Instant
import java.util.UUID

data class VideoProcessingRequested(
	val eventId: UUID,
	val eventType: String = EVENT_TYPE,
	val occurredAt: Instant,
	val videoId: UUID,
	val customerId: UUID,
	val originalFilename: String,
	val inputObjectKey: String,
) {
	companion object {
		const val EVENT_TYPE = "VideoProcessingRequested"
		const val TOPIC = "video.processing.requested"
	}
}

interface VideoProcessingRequestRegistration {
	fun save(videoProcessing: VideoProcessing, event: VideoProcessingRequested): VideoProcessing
}
