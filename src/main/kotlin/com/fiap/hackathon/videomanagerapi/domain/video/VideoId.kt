package com.fiap.hackathon.videomanagerapi.domain.video

import java.util.UUID

@JvmInline
value class VideoId(val value: UUID) {
	companion object {
		fun new(): VideoId = VideoId(UUID.randomUUID())
		fun from(value: String): VideoId = VideoId(UUID.fromString(value))
	}
}
