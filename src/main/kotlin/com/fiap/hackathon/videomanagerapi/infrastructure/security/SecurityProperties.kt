package com.fiap.hackathon.videomanagerapi.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.security")
data class SecurityProperties(
	val jwtSecret: String,
	val jwtIssuer: String = "customer-auth-api",
)
