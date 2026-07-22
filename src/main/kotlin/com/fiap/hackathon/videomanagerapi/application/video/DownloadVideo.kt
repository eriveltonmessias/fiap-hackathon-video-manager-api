package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import java.io.InputStream
import java.util.UUID

data class VideoDownload(
	val filename: String,
	val content: InputStream,
)

class VideoNotReadyForDownloadException(status: VideoStatus) :
	RuntimeException("Video result is not available for status $status")

class DownloadVideo(
	private val authenticatedCustomerProvider: AuthenticatedCustomerProvider,
	private val repository: VideoQueryRepository,
	private val storage: VideoStorage,
) {
	fun execute(videoId: UUID): VideoDownload {
		val customerId = authenticatedCustomerProvider.customerId()
		val video = repository.findByIdAndCustomerId(videoId, customerId) ?: throw VideoNotFoundException()
		if (video.status != VideoStatus.PROCESSED) {
			throw VideoNotReadyForDownloadException(video.status)
		}

		val outputObjectKey = checkNotNull(video.outputObjectKey) {
			"Processed video must have an output object key"
		}
		return VideoDownload(
			filename = "${video.id}-frames.zip",
			content = storage.download(StorageBucket.OUTPUT, outputObjectKey),
		)
	}
}
