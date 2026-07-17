package com.fiap.hackathon.videomanagerapi.domain.video

import java.util.UUID

@JvmInline
value class CustomerId(val value: UUID) {
	companion object {
		fun from(value: String): CustomerId = CustomerId(UUID.fromString(value))
	}
}
