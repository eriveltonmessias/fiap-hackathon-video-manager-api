package com.fiap.hackathon.videomanagerapi.infrastructure.storage

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.StorageBucket
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MinioVideoStorageIntegrationTest(
	@Autowired private val videoStorage: VideoStorage,
	@Autowired private val bucketInitializer: MinioBucketInitializer,
	@Autowired private val minioClient: MinioClient,
	@Autowired private val properties: MinioProperties,
) {
	@Test
	fun `initializes buckets idempotently and uploads and downloads object`() {
		bucketInitializer.initializeBuckets()
		bucketInitializer.initializeBuckets()

		assertTrue(bucketExists(properties.inputBucket))
		assertTrue(bucketExists(properties.outputBucket))

		val objectKey = ObjectKey.of("customers/customer-id/videos/video-id/source.mp4")
		val content = "fake-video-content".toByteArray()
		videoStorage.upload(
			bucket = StorageBucket.INPUT,
			objectKey = objectKey,
			content = ByteArrayInputStream(content),
			contentLength = content.size.toLong(),
			contentType = "video/mp4",
		)

		assertTrue(videoStorage.exists(StorageBucket.INPUT, objectKey))
		assertFalse(videoStorage.exists(StorageBucket.OUTPUT, objectKey))
		videoStorage.download(StorageBucket.INPUT, objectKey).use {
			assertContentEquals(content, it.readAllBytes())
		}
	}

	private fun bucketExists(bucket: String): Boolean = minioClient.bucketExists(
		BucketExistsArgs.builder().bucket(bucket).build(),
	)
}
