package com.fiap.hackathon.videomanagerapi.application.observability

import java.util.UUID

interface VideoLifecycleObserver {
	fun videoUploadAccepted(customerId: UUID, videoId: UUID, eventId: UUID, contentLength: Long) = Unit
	fun outboxPublished(eventId: UUID, videoId: String, topic: String, attempt: Int) = Unit
	fun outboxPublishFailed(eventId: UUID, videoId: String, topic: String, attempt: Int, cause: Exception) = Unit
	fun processingResultReceived(eventId: UUID, videoId: UUID, eventType: String) = Unit
	fun processingResultHandled(eventId: UUID, videoId: UUID, eventType: String, result: String) = Unit
	fun notificationCompleted(
		customerId: UUID,
		videoId: UUID,
		eventId: UUID,
		channel: String?,
		result: String,
	) = Unit

	companion object {
		val NONE: VideoLifecycleObserver = object : VideoLifecycleObserver {}
	}
}

inline fun VideoLifecycleObserver.observeSafely(action: VideoLifecycleObserver.() -> Unit) {
	try {
		action()
	} catch (_: Exception) {
		// Observability must never change the business operation result.
	}
}
