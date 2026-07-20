package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.domain.video.CustomerId
import com.fiap.hackathon.videomanagerapi.domain.video.FailureReason
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoId
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import org.springframework.stereotype.Component

@Component
class VideoProcessingJpaMapper {
	fun toEntity(videoProcessing: VideoProcessing): VideoProcessingJpaEntity = VideoProcessingJpaEntity(
		id = videoProcessing.id.value,
		customerId = videoProcessing.customerId.value,
		originalFilename = videoProcessing.originalFilename.value,
		status = videoProcessing.status,
		inputObjectKey = videoProcessing.inputObjectKey?.value,
		outputObjectKey = videoProcessing.outputObjectKey?.value,
		failureReason = videoProcessing.failureReason?.value,
		createdAt = videoProcessing.createdAt,
		updatedAt = videoProcessing.updatedAt,
	)

	fun toDomain(entity: VideoProcessingJpaEntity): VideoProcessing = VideoProcessing.restore(
		id = VideoId(entity.id),
		customerId = CustomerId(entity.customerId),
		originalFilename = OriginalFilename.of(entity.originalFilename),
		status = entity.status,
		inputObjectKey = entity.inputObjectKey?.let(ObjectKey::of),
		outputObjectKey = entity.outputObjectKey?.let(ObjectKey::of),
		failureReason = entity.failureReason?.let(FailureReason::of),
		createdAt = entity.createdAt,
		updatedAt = entity.updatedAt,
	)
}
