package com.fiap.hackathon.videomanagerapi.application.notification

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ProcessingCompletedNotificationMessage(
	val videoId: UUID,
	val originalFilename: String,
	val downloadUrl: String,
)

interface ProcessingCompletedNotificationSender {
	val channel: NotificationChannel
	fun send(preference: NotificationPreference, message: ProcessingCompletedNotificationMessage)
}

class NotifyVideoProcessingCompleted(
	private val repository: VideoProcessingRepository,
	private val preferenceProvider: NotificationPreferenceProvider,
	private val senders: List<ProcessingCompletedNotificationSender>,
	private val failureRecorder: NotificationFailureRecorder,
	private val publicBaseUrl: String,
	private val clock: Clock,
	private val observer: VideoLifecycleObserver = VideoLifecycleObserver.NONE,
) {
	fun execute(event: VideoProcessed): NotificationResult {
		val video = checkNotNull(repository.findById(event.videoId)) {
			"Video processing ${event.videoId} was not found after handling its success event"
		}
		var channel: NotificationChannel? = null
		return try {
			val preference = preferenceProvider.get(video.customerId)
			channel = preference.channel
			val sender = senders.singleOrNull { it.channel == preference.channel }
				?: throw NotificationDeliveryException("Notification channel is not configured")
			sender.send(
				preference,
				ProcessingCompletedNotificationMessage(
					videoId = video.id,
					originalFilename = video.originalFilename.value,
					downloadUrl = "${publicBaseUrl.trimEnd('/')}/videos/${video.id}/download",
				),
			)
			observer.observeSafely {
				notificationCompleted(
					video.customerId,
					video.id,
					event.eventId,
					channel.name,
					NotificationResult.SENT.name,
				)
			}
			NotificationResult.SENT
		} catch (exception: Exception) {
			val result = recordFailure(event, video.customerId, channel, safeReason(exception))
			observer.observeSafely {
				notificationCompleted(
					video.customerId,
					video.id,
					event.eventId,
					channel?.name,
					result.name,
				)
			}
			result
		}
	}

	private fun recordFailure(
		event: VideoProcessed,
		customerId: UUID,
		channel: NotificationChannel?,
		reason: String,
	): NotificationResult {
		failureRecorder.record(
			NotificationFailure(
				id = UUID.randomUUID(),
				eventId = event.eventId,
				videoId = event.videoId,
				customerId = customerId,
				channel = channel,
				reason = reason,
				failedAt = clock.instant(),
			),
		)
		return NotificationResult.FAILED
	}

	private fun safeReason(exception: Exception): String = when (exception) {
		is NotificationPreferenceUnavailableException -> "Customer notification preference is unavailable"
		is NotificationPreferenceNotFoundException -> "Customer notification preference was not found"
		is NotificationDeliveryException -> exception.message ?: "Notification delivery failed"
		else -> "Unexpected notification failure"
	}
}
