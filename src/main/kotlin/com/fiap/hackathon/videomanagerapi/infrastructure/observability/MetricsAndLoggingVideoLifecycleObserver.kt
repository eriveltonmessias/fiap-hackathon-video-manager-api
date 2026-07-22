package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MetricsAndLoggingVideoLifecycleObserver(
	private val meterRegistry: MeterRegistry,
) : VideoLifecycleObserver {
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun videoUploadAccepted(customerId: UUID, videoId: UUID, eventId: UUID, contentLength: Long) {
		meterRegistry.counter("video.manager.uploads", "outcome", "accepted").increment()
		meterRegistry.summary("video.manager.upload.size", "unit", "bytes").record(contentLength.toDouble())
		logger.atInfo()
			.addKeyValue("customerId", customerId)
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.log("Video upload accepted")
	}

	override fun outboxPublished(eventId: UUID, videoId: String, topic: String, attempt: Int) {
		meterRegistry.counter("video.manager.outbox.events", "outcome", "published", "topic", topic).increment()
		logger.atInfo()
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.addKeyValue("topic", topic)
			.addKeyValue("attempt", attempt)
			.log("Outbox event published")
	}

	override fun outboxPublishFailed(eventId: UUID, videoId: String, topic: String, attempt: Int, cause: Exception) {
		meterRegistry.counter("video.manager.outbox.events", "outcome", "failed", "topic", topic).increment()
		logger.atWarn()
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.addKeyValue("topic", topic)
			.addKeyValue("attempt", attempt)
			.addKeyValue("errorType", cause.javaClass.simpleName)
			.log("Outbox event publication failed")
	}

	override fun processingResultReceived(eventId: UUID, videoId: UUID, eventType: String) {
		logger.atInfo()
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.addKeyValue("eventType", eventType)
			.log("Video processing result received")
	}

	override fun processingResultHandled(eventId: UUID, videoId: UUID, eventType: String, result: String) {
		meterRegistry.counter(
			"video.manager.processing.results",
			"event.type",
			eventType,
			"outcome",
			result.lowercase(),
		).increment()
		logger.atInfo()
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.addKeyValue("eventType", eventType)
			.addKeyValue("result", result)
			.log("Video processing result handled")
	}

	override fun notificationCompleted(
		customerId: UUID,
		videoId: UUID,
		eventId: UUID,
		channel: String?,
		result: String,
	) {
		meterRegistry.counter(
			"video.manager.notifications",
			"channel",
			channel?.lowercase() ?: "unknown",
			"outcome",
			result.lowercase(),
		).increment()
		logger.atInfo()
			.addKeyValue("customerId", customerId)
			.addKeyValue("videoId", videoId)
			.addKeyValue("eventId", eventId)
			.addKeyValue("channel", channel ?: "unknown")
			.addKeyValue("result", result)
			.log("Failure notification attempt completed")
	}
}
