package com.fiap.hackathon.videomanagerapi.infrastructure.observability

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
	fun `reports unavailable MinIO within the configured timeout`() {
		val (health, elapsed) = measureTimedValue {
			MinioHealthIndicator("http://localhost:1", Duration.ofMillis(100)).health()
		}

		assertEquals(Status.DOWN, health.status)
		assertTrue(elapsed < kotlin.time.Duration.parse("1s"))
	}
}
