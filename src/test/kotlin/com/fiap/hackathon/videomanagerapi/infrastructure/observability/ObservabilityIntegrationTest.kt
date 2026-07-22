package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import com.fiap.hackathon.videomanagerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@SpringBootTest(properties = ["app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class ObservabilityIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
	@Autowired private val observer: VideoLifecycleObserver,
) {
	@Test
	fun `exposes application metrics in Prometheus format`() {
		observer.videoUploadAccepted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4096)

		mockMvc.get("/actuator/prometheus").andExpect {
			status { isOk() }
			content { string(org.hamcrest.Matchers.containsString("video_manager_uploads_total")) }
		}
	}

	@Test
	fun `separates liveness from infrastructure readiness`() {
		mockMvc.get("/actuator/health/liveness").andExpect {
			status { isOk() }
			jsonPath("$.status") { value("UP") }
			jsonPath("$.components.livenessState.status") { value("UP") }
		}

		mockMvc.get("/actuator/health/readiness").andExpect {
			status { isOk() }
			jsonPath("$.status") { value("UP") }
			jsonPath("$.components.db.status") { value("UP") }
			jsonPath("$.components.kafka.status") { value("UP") }
			jsonPath("$.components.minio.status") { value("UP") }
		}
	}
}
