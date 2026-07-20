package com.fiap.hackathon.videomanagerapi.infrastructure.security

import com.fiap.hackathon.videomanagerapi.application.video.AuthenticatedCustomerProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JwtAuthenticatedCustomerProvider : AuthenticatedCustomerProvider {
	override fun customerId(): UUID {
		val principal = SecurityContextHolder.getContext().authentication?.principal
		check(principal is Jwt) { "Authenticated JWT is required" }
		return UUID.fromString(principal.getClaimAsString(CustomerIdClaimValidator.CUSTOMER_ID_CLAIM))
	}
}
