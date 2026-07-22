package com.fiap.hackathon.videomanagerapi.application.outbox

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PendingOutboxEvent(
	val id: UUID,
	val topic: String,
	val key: String,
	val payload: String,
	val attempts: Int,
)

interface OutboxEventStore {
	fun lockPending(now: Instant, batchSize: Int): List<PendingOutboxEvent>
	fun markPublished(eventId: UUID, publishedAt: Instant)
	fun markFailed(eventId: UUID, nextAttemptAt: Instant, error: String)
}

fun interface OutboxEventPublisher {
	fun publish(event: PendingOutboxEvent)
}

class DispatchOutboxEvents(
	private val store: OutboxEventStore,
	private val publisher: OutboxEventPublisher,
	private val clock: Clock,
	private val retryDelay: Duration,
	private val observer: VideoLifecycleObserver = VideoLifecycleObserver.NONE,
) {
	fun execute(batchSize: Int): Int {
		require(batchSize > 0) { "batchSize must be positive" }
		val events = store.lockPending(clock.instant(), batchSize)
		events.forEach { event ->
			try {
				publisher.publish(event)
				store.markPublished(event.id, clock.instant())
				observer.observeSafely { outboxPublished(event.id, event.key, event.topic, event.attempts + 1) }
			} catch (exception: Exception) {
				store.markFailed(
					eventId = event.id,
					nextAttemptAt = clock.instant().plus(retryDelay),
					error = exception.safeMessage(),
				)
				observer.observeSafely {
					outboxPublishFailed(event.id, event.key, event.topic, event.attempts + 1, exception)
				}
			}
		}
		return events.size
	}

	private fun Exception.safeMessage(): String =
		(message?.takeIf(String::isNotBlank) ?: javaClass.simpleName).take(MAXIMUM_ERROR_LENGTH)

	private companion object {
		const val MAXIMUM_ERROR_LENGTH = 1000
	}
}
