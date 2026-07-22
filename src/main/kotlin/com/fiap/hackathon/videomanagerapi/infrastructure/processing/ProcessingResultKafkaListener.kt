package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import com.fiap.hackathon.videomanagerapi.application.notification.NotifyVideoProcessingFailure
import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import com.fiap.hackathon.videomanagerapi.application.video.HandlingResult
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingFailed
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ProcessingResultKafkaListener(
	private val objectMapper: ObjectMapper,
	private val handler: TransactionalVideoProcessingResultHandler,
	private val notifyVideoProcessingFailure: NotifyVideoProcessingFailure,
	private val observer: VideoLifecycleObserver,
) {
	@KafkaListener(topics = [VideoProcessed.TOPIC])
	fun consumeProcessed(payload: String) {
		val event = readEvent(payload, VideoProcessed.EVENT_TYPE, VideoProcessed::class.java)
		observer.observeSafely { processingResultReceived(event.eventId, event.videoId, event.eventType) }
		val result = handler.handle(event)
		observer.observeSafely { processingResultHandled(event.eventId, event.videoId, event.eventType, result.name) }
	}

	@KafkaListener(topics = [VideoProcessingFailed.TOPIC])
	fun consumeFailed(payload: String) {
		val event = readEvent(payload, VideoProcessingFailed.EVENT_TYPE, VideoProcessingFailed::class.java)
		observer.observeSafely { processingResultReceived(event.eventId, event.videoId, event.eventType) }
		val result = handler.handle(event)
		observer.observeSafely { processingResultHandled(event.eventId, event.videoId, event.eventType, result.name) }
		if (result == HandlingResult.PROCESSED) {
			notifyVideoProcessingFailure.execute(event)
		}
	}

	private fun <T : Any> readEvent(payload: String, expectedType: String, eventClass: Class<T>): T {
		val node = objectMapper.readTree(payload)
		require(node.isObject) { "Kafka event payload must be a JSON object" }
		require(node.path("eventType").stringValue() == expectedType) {
			"eventType must be $expectedType"
		}
		return objectMapper.treeToValue(node, eventClass)
	}
}
