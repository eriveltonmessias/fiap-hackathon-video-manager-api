package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.application.outbox.DispatchOutboxEvents
import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventPublisher
import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventStore
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties::class)
class OutboxConfig {
	@Bean
	fun dispatchOutboxEvents(
		store: OutboxEventStore,
		publisher: OutboxEventPublisher,
		clock: Clock,
		properties: OutboxProperties,
	): DispatchOutboxEvents = DispatchOutboxEvents(store, publisher, clock, properties.retryDelay)
}
