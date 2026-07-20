package com.fiap.hackathon.videomanagerapi.infrastructure.video

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties("app.video-upload")
data class VideoUploadProperties(
	val maximumFileSize: DataSize = DataSize.ofMegabytes(500),
	val allowedContentTypes: Set<String> = DEFAULT_CONTENT_TYPES,
	val allowedExtensions: Set<String> = DEFAULT_EXTENSIONS,
) {
	companion object {
		val DEFAULT_CONTENT_TYPES = setOf(
			"video/mp4",
			"video/quicktime",
			"video/x-msvideo",
			"video/x-matroska",
			"video/webm",
			"video/mpeg",
		)
		val DEFAULT_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm", "mpeg", "mpg")
	}
}
