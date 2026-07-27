package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Duration
import java.util.concurrent.TimeUnit

class KafkaHealthIndicator(
	configuration: Map<String, Any>,
	private val timeout: Duration,
) : HealthIndicator {
	private val configuration = configuration.toMutableMap().apply {
		val timeoutMillis = timeout.toMillis().toInt()
		this[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = timeoutMillis
		this[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = timeoutMillis
	}

	override fun health(): Health = try {
		val admin = Admin.create(configuration)
		val clusterId = try {
			admin.describeCluster().clusterId().get(timeout.toMillis(), TimeUnit.MILLISECONDS)
		} finally {
			admin.close(timeout)
		}
		Health.up().withDetail("clusterId", clusterId).build()
	} catch (exception: Exception) {
		Health.down().withDetail("errorType", exception.javaClass.simpleName).build()
	}
}

class MinioHealthIndicator(
	private val minioClient: MinioClient,
	private val bucketNames: List<String>,
) : HealthIndicator {
	override fun health(): Health = try {
		val missingBuckets = bucketNames.filterNot { bucketName ->
			minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
		}
		if (missingBuckets.isEmpty()) {
			Health.up().withDetail("buckets", bucketNames).build()
		} else {
			Health.down().withDetail("missingBuckets", missingBuckets).build()
		}
	} catch (exception: Exception) {
		Health.down().withDetail("errorType", exception.javaClass.simpleName).build()
	}
}
