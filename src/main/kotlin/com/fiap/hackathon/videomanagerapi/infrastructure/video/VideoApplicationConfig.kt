package com.fiap.hackathon.videomanagerapi.infrastructure.video

import com.fiap.hackathon.videomanagerapi.application.video.AuthenticatedCustomerProvider
import com.fiap.hackathon.videomanagerapi.application.video.GetVideo
import com.fiap.hackathon.videomanagerapi.application.video.ListVideos
import com.fiap.hackathon.videomanagerapi.application.video.UploadVideo
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoQueryRepository
import com.fiap.hackathon.videomanagerapi.application.video.VideoStorage
import com.fiap.hackathon.videomanagerapi.application.video.VideoUploadPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(VideoUploadProperties::class)
class VideoApplicationConfig {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun videoUploadPolicy(properties: VideoUploadProperties): VideoUploadPolicy = VideoUploadPolicy(
		maximumContentLength = properties.maximumFileSize.toBytes(),
		allowedContentTypes = properties.allowedContentTypes,
		allowedExtensions = properties.allowedExtensions,
	)

	@Bean
	fun uploadVideo(
		authenticatedCustomerProvider: AuthenticatedCustomerProvider,
		repository: VideoProcessingRepository,
		storage: VideoStorage,
		policy: VideoUploadPolicy,
		clock: Clock,
	): UploadVideo = UploadVideo(authenticatedCustomerProvider, repository, storage, policy, clock)

	@Bean
	fun getVideo(
		authenticatedCustomerProvider: AuthenticatedCustomerProvider,
		repository: VideoQueryRepository,
	): GetVideo = GetVideo(authenticatedCustomerProvider, repository)

	@Bean
	fun listVideos(
		authenticatedCustomerProvider: AuthenticatedCustomerProvider,
		repository: VideoQueryRepository,
	): ListVideos = ListVideos(authenticatedCustomerProvider, repository)
}
