package com.fiap.hackathon.videomanagerapi.application.video

class InvalidVideoUploadException(message: String) : RuntimeException(message)

class VideoTooLargeException(
	val maximumContentLength: Long,
) : RuntimeException("Video must have at most $maximumContentLength bytes")
