package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.processing-results")
data class ProcessingResultProperties(
	val retryInterval: Duration = Duration.ofSeconds(1),
	val maxRetries: Long = 2,
)
