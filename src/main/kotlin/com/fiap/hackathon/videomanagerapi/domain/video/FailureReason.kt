package com.fiap.hackathon.videomanagerapi.domain.video

@JvmInline
value class FailureReason private constructor(val value: String) {
	companion object {
		fun of(value: String): FailureReason {
			val normalized = value.trim()
			require(normalized.isNotEmpty()) { "failureReason must not be blank" }
			require(normalized.length <= 1000) { "failureReason must have at most 1000 characters" }
			return FailureReason(normalized)
		}
	}
}
