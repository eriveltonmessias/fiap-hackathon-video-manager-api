package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.InvalidVideoUploadException
import com.fiap.hackathon.videomanagerapi.application.video.VideoTooLargeException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import java.time.Instant

data class VideoApiError(
	val timestamp: Instant = Instant.now(),
	val status: Int,
	val error: String,
	val message: String,
	val path: String,
)

@RestControllerAdvice
class VideoExceptionHandler {
	@ExceptionHandler(InvalidVideoUploadException::class, MultipartException::class)
	fun badRequest(exception: RuntimeException, request: HttpServletRequest) =
		response(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid multipart request", request)

	@ExceptionHandler(VideoTooLargeException::class, MaxUploadSizeExceededException::class)
	fun payloadTooLarge(exception: RuntimeException, request: HttpServletRequest) =
		response(HttpStatus.PAYLOAD_TOO_LARGE, exception.message ?: "Video file is too large", request)

	private fun response(
		status: HttpStatus,
		message: String,
		request: HttpServletRequest,
	): ResponseEntity<VideoApiError> = ResponseEntity.status(status).body(
		VideoApiError(
			status = status.value(),
			error = status.reasonPhrase,
			message = message,
			path = request.requestURI,
		),
	)
}
