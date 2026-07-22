package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
	endpoint: String,
	private val timeout: Duration,
) : HealthIndicator {
	private val healthUri = URI.create("${endpoint.trimEnd('/')}/minio/health/live")
	private val httpClient = HttpClient.newBuilder().connectTimeout(timeout).build()

	override fun health(): Health = try {
		val request = HttpRequest.newBuilder(healthUri).timeout(timeout).GET().build()
		val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
		if (response.statusCode() in 200..299) {
			Health.up().build()
		} else {
			Health.down().withDetail("statusCode", response.statusCode()).build()
		}
	} catch (exception: Exception) {
		Health.down().withDetail("errorType", exception.javaClass.simpleName).build()
	}
}
