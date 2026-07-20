package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename

class VideoUploadPolicy(
	private val maximumContentLength: Long,
	allowedContentTypes: Set<String>,
	allowedExtensions: Set<String>,
) {
	private val allowedContentTypes = allowedContentTypes.mapTo(mutableSetOf()) { it.lowercase() }
	private val allowedExtensions = allowedExtensions.mapTo(mutableSetOf()) { it.lowercase().removePrefix(".") }

	init {
		require(maximumContentLength > 0) { "maximumContentLength must be positive" }
		require(this.allowedContentTypes.isNotEmpty()) { "allowedContentTypes must not be empty" }
		require(this.allowedExtensions.isNotEmpty()) { "allowedExtensions must not be empty" }
	}

	fun validate(
		filename: OriginalFilename,
		contentLength: Long,
		contentType: String?,
	): String {
		if (contentLength <= 0) throw InvalidVideoUploadException("Video file must not be empty")
		if (contentLength > maximumContentLength) throw VideoTooLargeException(maximumContentLength)

		val basename = filename.value.substringBeforeLast('.', missingDelimiterValue = "")
		val extension = filename.value.substringAfterLast('.', missingDelimiterValue = "").lowercase()
		if (basename.isBlank() || extension !in allowedExtensions) {
			throw InvalidVideoUploadException("Video filename has an unsupported extension")
		}

		val normalizedContentType = contentType?.trim()?.lowercase()
		if (normalizedContentType.isNullOrEmpty() || normalizedContentType !in allowedContentTypes) {
			throw InvalidVideoUploadException("Video content type is not supported")
		}
		return normalizedContentType
	}
}
