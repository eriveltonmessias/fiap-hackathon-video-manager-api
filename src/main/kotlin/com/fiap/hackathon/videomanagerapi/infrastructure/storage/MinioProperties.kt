package com.fiap.hackathon.videomanagerapi.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.storage.minio")
data class MinioProperties(
	val endpoint: String,
	val accessKey: String,
	val secretKey: String,
	val inputBucket: String = "fiapx-videos-input",
	val outputBucket: String = "fiapx-videos-output",
)
