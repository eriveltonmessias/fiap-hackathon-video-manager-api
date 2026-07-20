package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.outbox")
data class OutboxProperties(
	val batchSize: Int = 50,
	val retryDelay: Duration = Duration.ofSeconds(5),
	val sendTimeout: Duration = Duration.ofSeconds(10),
)
