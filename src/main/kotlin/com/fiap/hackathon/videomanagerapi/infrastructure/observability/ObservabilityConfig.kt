package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import com.fiap.hackathon.videomanagerapi.infrastructure.storage.MinioProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaAdmin
import java.time.Duration

@ConfigurationProperties("app.observability")
data class ObservabilityProperties(
	val healthTimeout: Duration = Duration.ofSeconds(2),
)

@Configuration
@EnableConfigurationProperties(ObservabilityProperties::class)
class ObservabilityConfig {
	@Bean
	fun kafkaHealthIndicator(kafkaAdmin: KafkaAdmin, properties: ObservabilityProperties): KafkaHealthIndicator =
		KafkaHealthIndicator(kafkaAdmin.configurationProperties, properties.healthTimeout)

	@Bean
	fun minioHealthIndicator(
		minioProperties: MinioProperties,
		properties: ObservabilityProperties,
	): MinioHealthIndicator = MinioHealthIndicator(minioProperties.endpoint, properties.healthTimeout)
}
