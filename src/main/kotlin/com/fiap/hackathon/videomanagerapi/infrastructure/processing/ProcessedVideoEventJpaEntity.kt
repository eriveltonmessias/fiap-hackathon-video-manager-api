package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "processed_video_events")
class ProcessedVideoEventJpaEntity(
	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	var eventId: UUID,

	@Column(name = "video_id", nullable = false, updatable = false)
	var videoId: UUID,

	@Column(name = "event_type", nullable = false, updatable = false, length = 80)
	var eventType: String,

	@Column(name = "processed_at", nullable = false, updatable = false)
	var processedAt: Instant,
)
