package com.fiap.hackathon.videomanagerapi.application.notification

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingFailed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class NotificationChannel {
	EMAIL,
	TELEGRAM,
}

data class NotificationPreference(
	val customerId: UUID,
	val channel: NotificationChannel,
	val email: String,
	val telegramChatId: String?,
)

data class FailureNotificationMessage(
	val videoId: UUID,
	val originalFilename: String,
	val failureReason: String,
)

fun interface NotificationPreferenceProvider {
	fun get(customerId: UUID): NotificationPreference
}

interface FailureNotificationSender {
	val channel: NotificationChannel
	fun send(preference: NotificationPreference, message: FailureNotificationMessage)
}

data class NotificationFailure(
	val id: UUID,
	val eventId: UUID,
	val videoId: UUID,
	val customerId: UUID,
	val channel: NotificationChannel?,
	val reason: String,
	val failedAt: Instant,
)

fun interface NotificationFailureRecorder {
	fun record(failure: NotificationFailure)
}

enum class NotificationResult {
	SENT,
	FAILED,
}

class NotifyVideoProcessingFailure(
	private val repository: VideoProcessingRepository,
	private val preferenceProvider: NotificationPreferenceProvider,
	private val senders: List<FailureNotificationSender>,
	private val failureRecorder: NotificationFailureRecorder,
	private val clock: Clock,
	private val observer: VideoLifecycleObserver = VideoLifecycleObserver.NONE,
) {
	fun execute(event: VideoProcessingFailed): NotificationResult {
		val video = checkNotNull(repository.findById(event.videoId)) {
			"Video processing ${event.videoId} was not found after handling its failure event"
		}
		var channel: NotificationChannel? = null
		return try {
			val preference = preferenceProvider.get(video.customerId)
			channel = preference.channel
			val sender = senders.singleOrNull { it.channel == preference.channel }
				?: throw NotificationDeliveryException("Notification channel is not configured")
			sender.send(
				preference,
				FailureNotificationMessage(
					videoId = video.id,
					originalFilename = video.originalFilename.value,
					failureReason = event.failureReason,
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
		event: VideoProcessingFailed,
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

class NotificationPreferenceUnavailableException(cause: Throwable? = null) :
	RuntimeException("Customer notification preference is unavailable", cause)

class NotificationPreferenceNotFoundException :
	RuntimeException("Customer notification preference was not found")

class NotificationDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
