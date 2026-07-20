package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.application.outbox.OutboxEventStore
import com.fiap.hackathon.videomanagerapi.application.outbox.PendingOutboxEvent
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaOutboxEventStore(
	private val repository: SpringDataOutboxEventRepository,
) : OutboxEventStore {
	override fun lockPending(now: Instant, batchSize: Int): List<PendingOutboxEvent> =
		repository.lockPending(now, batchSize).map {
			PendingOutboxEvent(it.id, it.topic, it.eventKey, it.payload, it.attempts)
		}

	override fun markPublished(eventId: UUID, publishedAt: Instant) {
		val event = requireNotNull(repository.findByIdOrNull(eventId)) { "Outbox event $eventId not found" }
		event.publishedAt = publishedAt
		event.attempts += 1
		event.lastError = null
	}

	override fun markFailed(eventId: UUID, nextAttemptAt: Instant, error: String) {
		val event = requireNotNull(repository.findByIdOrNull(eventId)) { "Outbox event $eventId not found" }
		event.attempts += 1
		event.nextAttemptAt = nextAttemptAt
		event.lastError = error
	}
}
