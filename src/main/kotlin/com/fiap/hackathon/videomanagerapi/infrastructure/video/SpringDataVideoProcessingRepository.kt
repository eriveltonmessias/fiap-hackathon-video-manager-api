package com.fiap.hackathon.videomanagerapi.infrastructure.video

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataVideoProcessingRepository : JpaRepository<VideoProcessingJpaEntity, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select video from VideoProcessingJpaEntity video where video.id = :id")
	fun findByIdForUpdate(id: UUID): VideoProcessingJpaEntity?

	fun findByIdAndCustomerId(id: UUID, customerId: UUID): VideoProcessingJpaEntity?
	fun findAllByCustomerId(customerId: UUID, pageable: Pageable): Page<VideoProcessingJpaEntity>
}
