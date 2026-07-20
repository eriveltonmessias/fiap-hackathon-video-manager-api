package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.GetVideo
import com.fiap.hackathon.videomanagerapi.application.video.ListVideos
import com.fiap.hackathon.videomanagerapi.application.video.UploadVideo
import com.fiap.hackathon.videomanagerapi.application.video.UploadVideoCommand
import com.fiap.hackathon.videomanagerapi.application.video.VideoPage
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

data class UploadVideoResponse(
	val videoId: UUID,
	val status: VideoStatus,
)

data class VideoResponse(
	val videoId: UUID,
	val originalFilename: String,
	val status: VideoStatus,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class VideoPageResponse(
	val content: List<VideoResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
)

@RestController
@RequestMapping("/videos")
class VideoController(
	private val uploadVideo: UploadVideo,
	private val getVideo: GetVideo,
	private val listVideos: ListVideos,
) {
	@PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	@ResponseStatus(HttpStatus.ACCEPTED)
	fun upload(@RequestPart("file") file: MultipartFile): UploadVideoResponse = file.inputStream.use { content ->
		val result = uploadVideo.execute(
			UploadVideoCommand(
				originalFilename = file.originalFilename,
				contentType = file.contentType,
				contentLength = file.size,
				content = content,
			),
		)
		UploadVideoResponse(result.videoId, result.status)
	}

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): VideoPageResponse = listVideos.execute(page, size).toResponse()

	@GetMapping("/{videoId}")
	fun get(@PathVariable videoId: UUID): VideoResponse = getVideo.execute(videoId).toResponse()
}

private fun VideoProcessing.toResponse(): VideoResponse = VideoResponse(
	videoId = id,
	originalFilename = originalFilename.value,
	status = status,
	createdAt = createdAt,
	updatedAt = updatedAt,
)

private fun VideoPage.toResponse(): VideoPageResponse = VideoPageResponse(
	content = content.map { it.toResponse() },
	page = page,
	size = size,
	totalElements = totalElements,
	totalPages = totalPages,
)
