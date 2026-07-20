package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaVideoProcessingRepository(
	private val repository: SpringDataVideoProcessingRepository,
	private val mapper: VideoProcessingJpaMapper,
) : VideoProcessingRepository {
	override fun save(videoProcessing: VideoProcessing): VideoProcessing =
		mapper.toDomain(repository.saveAndFlush(mapper.toEntity(videoProcessing)))

	override fun findById(id: UUID): VideoProcessing? =
		repository.findByIdOrNull(id)?.let(mapper::toDomain)
}
