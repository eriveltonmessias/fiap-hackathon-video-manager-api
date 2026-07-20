package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.application.outbox.DispatchOutboxEvents
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalOutboxDispatcher(
	private val dispatchOutboxEvents: DispatchOutboxEvents,
	private val properties: OutboxProperties,
) {
	@Transactional
	fun dispatch(): Int = dispatchOutboxEvents.execute(properties.batchSize)
}
