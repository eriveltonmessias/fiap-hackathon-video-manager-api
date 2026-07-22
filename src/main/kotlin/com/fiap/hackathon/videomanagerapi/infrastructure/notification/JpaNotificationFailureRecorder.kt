package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.NotificationFailure
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationFailureRecorder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

interface SpringDataNotificationFailureRepository : JpaRepository<NotificationFailureJpaEntity, UUID> {
	fun findByEventId(eventId: UUID): NotificationFailureJpaEntity?
}

@Repository
class JpaNotificationFailureRecorder(
	private val repository: SpringDataNotificationFailureRepository,
) : NotificationFailureRecorder {
	override fun record(failure: NotificationFailure) {
		repository.saveAndFlush(
			NotificationFailureJpaEntity(
				id = failure.id,
				eventId = failure.eventId,
				videoId = failure.videoId,
				customerId = failure.customerId,
				channel = failure.channel,
				reason = failure.reason,
				failedAt = failure.failedAt,
			),
		)
	}
}
