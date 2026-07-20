package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.util.UUID

class VideoNotFoundException : RuntimeException("Video not found")

class InvalidVideoQueryException(message: String) : RuntimeException(message)

class GetVideo(
	private val authenticatedCustomerProvider: AuthenticatedCustomerProvider,
	private val repository: VideoQueryRepository,
) {
	fun execute(videoId: UUID): VideoProcessing {
		val customerId = authenticatedCustomerProvider.customerId()
		return repository.findByIdAndCustomerId(videoId, customerId) ?: throw VideoNotFoundException()
	}
}

class ListVideos(
	private val authenticatedCustomerProvider: AuthenticatedCustomerProvider,
	private val repository: VideoQueryRepository,
) {
	fun execute(page: Int, size: Int): VideoPage {
		val pageRequest = VideoPageRequest(page, size)
		return repository.findAllByCustomerId(authenticatedCustomerProvider.customerId(), pageRequest)
	}
}
