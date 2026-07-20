package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "video_processings")
class VideoProcessingJpaEntity(
	@Id
	@Column(nullable = false, updatable = false)
	var id: UUID,

	@Column(name = "customer_id", nullable = false, updatable = false)
	var customerId: UUID,

	@Column(name = "original_filename", nullable = false, updatable = false, length = 255)
	var originalFilename: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	var status: VideoStatus,

	@Column(name = "input_object_key", length = 1024)
	var inputObjectKey: String?,

	@Column(name = "output_object_key", length = 1024)
	var outputObjectKey: String?,

	@Column(name = "failure_reason", length = 1000)
	var failureReason: String?,

	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant,

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant,
)
