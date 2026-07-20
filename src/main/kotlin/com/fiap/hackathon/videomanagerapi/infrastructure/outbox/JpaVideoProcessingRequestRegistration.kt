package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRequestRegistration
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRequested
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.infrastructure.video.SpringDataVideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.infrastructure.video.VideoProcessingJpaMapper
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Repository
class JpaVideoProcessingRequestRegistration(
	private val videoRepository: SpringDataVideoProcessingRepository,
	private val entityManager: EntityManager,
	private val mapper: VideoProcessingJpaMapper,
	private val objectMapper: ObjectMapper,
) : VideoProcessingRequestRegistration {
	@Transactional
	override fun save(videoProcessing: VideoProcessing, event: VideoProcessingRequested): VideoProcessing {
		val saved = videoRepository.save(mapper.toEntity(videoProcessing))
		entityManager.persist(
			OutboxEventJpaEntity(
				id = event.eventId,
				aggregateId = event.videoId,
				eventType = event.eventType,
				topic = VideoProcessingRequested.TOPIC,
				eventKey = event.videoId.toString(),
				payload = objectMapper.writeValueAsString(event),
				occurredAt = event.occurredAt,
				publishedAt = null,
				attempts = 0,
				nextAttemptAt = event.occurredAt,
				lastError = null,
			),
		)
		entityManager.flush()
		return mapper.toDomain(saved)
	}
}
