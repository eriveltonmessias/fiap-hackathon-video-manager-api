package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import com.fiap.hackathon.videomanagerapi.application.video.ProcessedVideoEventRegistry
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaProcessedVideoEventRegistry(
	private val repository: SpringDataProcessedVideoEventRepository,
) : ProcessedVideoEventRegistry {
	override fun register(eventId: UUID, videoId: UUID, eventType: String, processedAt: Instant): Boolean =
		repository.insertIfAbsent(eventId, videoId, eventType, processedAt) == 1
}
