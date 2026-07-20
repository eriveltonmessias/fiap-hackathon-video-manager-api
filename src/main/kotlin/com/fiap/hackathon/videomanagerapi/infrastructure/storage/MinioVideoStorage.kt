package com.fiap.hackathon.videomanagerapi.infrastructure.storage

import com.fiap.hackathon.videomanagerapi.application.video.StorageBucket
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class MinioVideoStorage(
	private val minioClient: MinioClient,
	private val properties: MinioProperties,
) : VideoStorage {
	override fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String?,
	) {
		require(contentLength >= 0) { "contentLength must not be negative" }
		require(contentType == null || contentType.isNotBlank()) { "contentType must not be blank" }

		val builder = PutObjectArgs.builder()
			.bucket(bucketName(bucket))
			.`object`(objectKey.value)
			.stream(content, contentLength, MULTIPART_PART_SIZE)
		contentType?.let(builder::contentType)
		minioClient.putObject(builder.build())
	}

	override fun exists(bucket: StorageBucket, objectKey: ObjectKey): Boolean = try {
		minioClient.statObject(
			StatObjectArgs.builder().bucket(bucketName(bucket)).`object`(objectKey.value).build(),
		)
		true
	} catch (exception: ErrorResponseException) {
		if (exception.errorResponse().code() in MISSING_OBJECT_CODES) false else throw exception
	}

	override fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream =
		minioClient.getObject(
			GetObjectArgs.builder().bucket(bucketName(bucket)).`object`(objectKey.value).build(),
		)

	private fun bucketName(bucket: StorageBucket): String = when (bucket) {
		StorageBucket.INPUT -> properties.inputBucket
		StorageBucket.OUTPUT -> properties.outputBucket
	}

	private companion object {
		const val MULTIPART_PART_SIZE = 10L * 1024 * 1024
		val MISSING_OBJECT_CODES = setOf("NoSuchKey", "NoSuchObject", "NotFound")
	}
}
