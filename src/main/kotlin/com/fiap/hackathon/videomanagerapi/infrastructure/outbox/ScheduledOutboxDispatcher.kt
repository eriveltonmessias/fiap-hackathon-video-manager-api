package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = ["scheduling-enabled"], havingValue = "true", matchIfMissing = true)
class ScheduledOutboxDispatcher(
	private val dispatcher: TransactionalOutboxDispatcher,
) {
	@Scheduled(fixedDelayString = "\${app.outbox.fixed-delay:1s}")
	fun dispatch() {
		dispatcher.dispatch()
	}
}
