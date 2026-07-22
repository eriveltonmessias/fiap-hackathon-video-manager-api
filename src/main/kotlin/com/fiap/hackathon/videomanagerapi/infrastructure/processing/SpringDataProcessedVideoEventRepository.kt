package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SpringDataProcessedVideoEventRepository : JpaRepository<ProcessedVideoEventJpaEntity, UUID> {
	@Modifying
	@Query(
		value = """
			INSERT INTO processed_video_events (event_id, video_id, event_type, processed_at)
			VALUES (:eventId, :videoId, :eventType, :processedAt)
			ON CONFLICT (event_id) DO NOTHING
		""",
		nativeQuery = true,
	)
	fun insertIfAbsent(eventId: UUID, videoId: UUID, eventType: String, processedAt: Instant): Int
}
