package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import io.minio.MinioClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.boot.health.contributor.Status
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

class InfrastructureHealthIndicatorsTest {
	@Test
	fun `reports unavailable Kafka within the configured timeout`() {
		val (health, elapsed) = measureTimedValue {
			KafkaHealthIndicator(
				mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:1"),
				Duration.ofMillis(100),
			).health()
		}

		assertEquals(Status.DOWN, health.status)
		assertTrue(elapsed < kotlin.time.Duration.parse("1s"))
	}

	@Test
	fun `reports unavailable object storage`() {
		val minioClient = MinioClient.builder()
			.endpoint("http://localhost:1")
			.credentials("access-key", "secret-key")
			.build()

		val health = MinioHealthIndicator(minioClient, listOf("input", "output")).health()

		assertEquals(Status.DOWN, health.status)
	}
}
