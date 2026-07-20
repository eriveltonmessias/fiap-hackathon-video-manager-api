package com.fiap.hackathon.videomanagerapi.application.outbox

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchOutboxEventsTest {
	private val now = Instant.parse("2026-07-20T15:00:00Z")
	private val event = PendingOutboxEvent(
		id = UUID.fromString("c35cf8e7-f12a-4928-a695-2fc739e762e6"),
		topic = "video.processing.requested",
		key = "video-id",
		payload = "{}",
		attempts = 0,
	)
	private val store = RecordingOutboxEventStore(event)
	private val publisher = FailingOnceOutboxPublisher()
	private val dispatcher = DispatchOutboxEvents(
		store = store,
		publisher = publisher,
		clock = Clock.fixed(now, ZoneOffset.UTC),
		retryDelay = Duration.ZERO,
	)

	@Test
	fun `keeps failed event pending and publishes it on next attempt`() {
		assertEquals(1, dispatcher.execute(10))
		assertFalse(store.published)
		assertEquals("Kafka unavailable", store.lastError)

		assertEquals(1, dispatcher.execute(10))
		assertTrue(store.published)
		assertEquals(2, publisher.attempts)
		assertEquals(0, dispatcher.execute(10))
	}
}

private class RecordingOutboxEventStore(
	private val event: PendingOutboxEvent,
) : OutboxEventStore {
	var published = false
	var lastError: String? = null

	override fun lockPending(now: Instant, batchSize: Int): List<PendingOutboxEvent> =
		if (published) emptyList() else listOf(event)

	override fun markPublished(eventId: UUID, publishedAt: Instant) {
		published = true
		lastError = null
	}

	override fun markFailed(eventId: UUID, nextAttemptAt: Instant, error: String) {
		lastError = error
	}
}

private class FailingOnceOutboxPublisher : OutboxEventPublisher {
	var attempts = 0

	override fun publish(event: PendingOutboxEvent) {
		attempts += 1
		if (attempts == 1) throw IllegalStateException("Kafka unavailable")
	}
}
