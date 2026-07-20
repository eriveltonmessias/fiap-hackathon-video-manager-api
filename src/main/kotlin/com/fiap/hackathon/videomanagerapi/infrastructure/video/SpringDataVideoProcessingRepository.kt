package com.fiap.hackathon.videomanagerapi.infrastructure.video

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataVideoProcessingRepository : JpaRepository<VideoProcessingJpaEntity, UUID>
