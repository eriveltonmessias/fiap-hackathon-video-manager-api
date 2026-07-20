package com.fiap.hackathon.videomanagerapi.infrastructure.video

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataVideoProcessingRepository : JpaRepository<VideoProcessingJpaEntity, UUID> {
	fun findByIdAndCustomerId(id: UUID, customerId: UUID): VideoProcessingJpaEntity?
	fun findAllByCustomerId(customerId: UUID, pageable: Pageable): Page<VideoProcessingJpaEntity>
}
