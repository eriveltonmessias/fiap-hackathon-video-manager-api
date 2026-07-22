package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.FailureNotificationSender
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationFailureRecorder
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreferenceUnavailableException
import com.fiap.hackathon.videomanagerapi.application.notification.NotifyVideoProcessingFailure
import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock

@Configuration
@EnableConfigurationProperties(CustomerAuthProperties::class, NotificationProperties::class)
class NotificationConfig {
	@Bean
	fun customerAuthRestClient(properties: CustomerAuthProperties): RestClient = RestClient.builder()
		.baseUrl(properties.baseUrl)
		.defaultHeader("X-Internal-Api-Key", properties.internalApiKey)
		.requestFactory(requestFactory(properties.connectTimeout, properties.readTimeout))
		.build()

	@Bean
	fun customerAuthRetry(properties: CustomerAuthProperties): Retry = Retry.of(
		"customer-auth-notification-preference",
		RetryConfig.custom<Any>()
			.maxAttempts(properties.retryMaxAttempts)
			.waitDuration(properties.retryDelay)
			.retryExceptions(NotificationPreferenceUnavailableException::class.java)
			.build(),
	)

	@Bean
	fun customerAuthCircuitBreaker(properties: CustomerAuthProperties): CircuitBreaker = CircuitBreaker.of(
		"customer-auth-notification-preference",
		CircuitBreakerConfig.custom()
			.slidingWindowSize(properties.circuitBreakerWindowSize)
			.minimumNumberOfCalls(properties.circuitBreakerMinimumCalls)
			.failureRateThreshold(properties.circuitBreakerFailureRate)
			.waitDurationInOpenState(properties.circuitBreakerOpenDuration)
			.recordExceptions(NotificationPreferenceUnavailableException::class.java)
			.build(),
	)

	@Bean
	fun notificationPreferenceProvider(
		@Qualifier("customerAuthRestClient") restClient: RestClient,
		retry: Retry,
		circuitBreaker: CircuitBreaker,
	) = CustomerAuthNotificationPreferenceProvider(restClient, retry, circuitBreaker)

	@Bean
	fun telegramRestClient(properties: NotificationProperties): RestClient = RestClient.builder()
		.baseUrl("https://api.telegram.org")
		.defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.requestFactory(requestFactory(properties.telegramConnectTimeout, properties.telegramReadTimeout))
		.build()

	@Bean
	fun emailFailureNotificationSender(
		mailSender: JavaMailSender,
		properties: NotificationProperties,
	): FailureNotificationSender = EmailFailureNotificationSender(mailSender, properties)

	@Bean
	fun telegramFailureNotificationSender(
		@Qualifier("telegramRestClient") restClient: RestClient,
		properties: NotificationProperties,
	): FailureNotificationSender = TelegramFailureNotificationSender(restClient, properties)

	@Bean
	fun notifyVideoProcessingFailure(
		repository: VideoProcessingRepository,
		preferenceProvider: CustomerAuthNotificationPreferenceProvider,
		senders: List<FailureNotificationSender>,
		failureRecorder: NotificationFailureRecorder,
		clock: Clock,
		observer: VideoLifecycleObserver,
	): NotifyVideoProcessingFailure = NotifyVideoProcessingFailure(
		repository,
		preferenceProvider,
		senders,
		failureRecorder,
		clock,
		observer,
	)

	private fun requestFactory(
		connectTimeout: java.time.Duration,
		readTimeout: java.time.Duration,
	): JdkClientHttpRequestFactory {
		val httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build()
		return JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(readTimeout) }
	}
}
