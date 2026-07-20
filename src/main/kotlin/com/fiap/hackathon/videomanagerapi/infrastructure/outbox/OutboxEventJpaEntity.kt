package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity(
	@Id
	@Column(nullable = false, updatable = false)
	var id: UUID,

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	var aggregateId: UUID,

	@Column(name = "event_type", nullable = false, updatable = false, length = 100)
	var eventType: String,

	@Column(nullable = false, updatable = false, length = 200)
	var topic: String,

	@Column(name = "event_key", nullable = false, updatable = false, length = 200)
	var eventKey: String,

	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	@ColumnTransformer(write = "?::jsonb")
	var payload: String,

	@Column(name = "occurred_at", nullable = false, updatable = false)
	var occurredAt: Instant,

	@Column(name = "published_at")
	var publishedAt: Instant?,

	@Column(nullable = false)
	var attempts: Int,

	@Column(name = "next_attempt_at", nullable = false)
	var nextAttemptAt: Instant,

	@Column(name = "last_error", length = 1000)
	var lastError: String?,
)
