package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import java.io.InputStream

interface VideoStorage {
	fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String? = null,
	)

	fun exists(bucket: StorageBucket, objectKey: ObjectKey): Boolean

	fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream
}
