package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.customer-auth")
data class CustomerAuthProperties(
	val baseUrl: String,
	val internalApiKey: String,
	val connectTimeout: Duration = Duration.ofMillis(500),
	val readTimeout: Duration = Duration.ofSeconds(1),
	val retryMaxAttempts: Int = 3,
	val retryDelay: Duration = Duration.ofMillis(100),
	val circuitBreakerWindowSize: Int = 5,
	val circuitBreakerMinimumCalls: Int = 3,
	val circuitBreakerFailureRate: Float = 50f,
	val circuitBreakerOpenDuration: Duration = Duration.ofSeconds(10),
)

@ConfigurationProperties("app.notifications")
data class NotificationProperties(
	val emailFrom: String = "no-reply@fiapx.local",
	val telegramBotToken: String = "",
	val telegramConnectTimeout: Duration = Duration.ofMillis(500),
	val telegramReadTimeout: Duration = Duration.ofSeconds(1),
)
