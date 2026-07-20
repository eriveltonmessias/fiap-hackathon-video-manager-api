package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.VideoPage
import com.fiap.hackathon.videomanagerapi.application.video.VideoPageRequest
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoQueryRepository
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaVideoProcessingRepository(
	private val repository: SpringDataVideoProcessingRepository,
	private val mapper: VideoProcessingJpaMapper,
) : VideoProcessingRepository, VideoQueryRepository {
	override fun save(videoProcessing: VideoProcessing): VideoProcessing =
		mapper.toDomain(repository.saveAndFlush(mapper.toEntity(videoProcessing)))

	override fun findById(id: UUID): VideoProcessing? =
		repository.findByIdOrNull(id)?.let(mapper::toDomain)

	override fun findByIdAndCustomerId(id: UUID, customerId: UUID): VideoProcessing? =
		repository.findByIdAndCustomerId(id, customerId)?.let(mapper::toDomain)

	override fun findAllByCustomerId(customerId: UUID, pageRequest: VideoPageRequest): VideoPage {
		val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
		val page = repository.findAllByCustomerId(
			customerId,
			PageRequest.of(pageRequest.page, pageRequest.size, sort),
		)
		return VideoPage(
			content = page.content.map(mapper::toDomain),
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages,
		)
	}
}
