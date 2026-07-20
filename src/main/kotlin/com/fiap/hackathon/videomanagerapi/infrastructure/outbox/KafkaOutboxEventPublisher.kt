package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventPublisher
import com.fiap.hackathon.videomanagerapi.application.outbox.PendingOutboxEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class KafkaOutboxEventPublisher(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val properties: OutboxProperties,
) : OutboxEventPublisher {
	override fun publish(event: PendingOutboxEvent) {
		kafkaTemplate.send(event.topic, event.key, event.payload)
			.get(properties.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
	}
}
