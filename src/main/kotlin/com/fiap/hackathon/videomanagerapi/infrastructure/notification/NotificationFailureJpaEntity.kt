package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.NotificationChannel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_failures")
class NotificationFailureJpaEntity(
	@Id
	@Column(nullable = false, updatable = false)
	var id: UUID,

	@Column(name = "event_id", nullable = false, updatable = false, unique = true)
	var eventId: UUID,

	@Column(name = "video_id", nullable = false, updatable = false)
	var videoId: UUID,

	@Column(name = "customer_id", nullable = false, updatable = false)
	var customerId: UUID,

	@Enumerated(EnumType.STRING)
	@Column(length = 20, updatable = false)
	var channel: NotificationChannel?,

	@Column(nullable = false, updatable = false, length = 500)
	var reason: String,

	@Column(name = "failed_at", nullable = false, updatable = false)
	var failedAt: Instant,
)
