package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.util.UUID

data class VideoPageRequest(
	val page: Int,
	val size: Int,
) {
	init {
		if (page < 0) throw InvalidVideoQueryException("Page must not be negative")
		if (size !in 1..MAXIMUM_PAGE_SIZE) {
			throw InvalidVideoQueryException("Page size must be between 1 and $MAXIMUM_PAGE_SIZE")
		}
	}

	private companion object {
		const val MAXIMUM_PAGE_SIZE = 100
	}
}

data class VideoPage(
	val content: List<VideoProcessing>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
)

interface VideoQueryRepository {
	fun findByIdAndCustomerId(id: UUID, customerId: UUID): VideoProcessing?
	fun findAllByCustomerId(customerId: UUID, pageRequest: VideoPageRequest): VideoPage
}
