package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.UploadVideo
import com.fiap.hackathon.videomanagerapi.application.video.UploadVideoCommand
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class UploadVideoResponse(
	val videoId: UUID,
	val status: VideoStatus,
)

@RestController
@RequestMapping("/videos")
class VideoController(
	private val uploadVideo: UploadVideo,
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
}
