package com.fiap.hackathon.videomanagerapi.infrastructure.security

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

class CustomerIdClaimValidator : OAuth2TokenValidator<Jwt> {
	override fun validate(token: Jwt): OAuth2TokenValidatorResult {
		val customerId = token.getClaimAsString(CUSTOMER_ID_CLAIM)
		return if (customerId.isValidUuid()) {
			OAuth2TokenValidatorResult.success()
		} else {
			OAuth2TokenValidatorResult.failure(
				OAuth2Error("invalid_token", "JWT customer_id claim must be a valid UUID", null),
			)
		}
	}

	private fun String?.isValidUuid(): Boolean = try {
		this != null && UUID.fromString(this).toString() == this.lowercase()
	} catch (_: IllegalArgumentException) {
		false
	}

	companion object {
		const val CUSTOMER_ID_CLAIM = "customer_id"
	}
}
