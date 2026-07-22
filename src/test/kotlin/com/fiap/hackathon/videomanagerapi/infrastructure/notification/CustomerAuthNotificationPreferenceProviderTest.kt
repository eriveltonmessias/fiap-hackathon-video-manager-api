package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.sun.net.httpserver.HttpServer
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationChannel
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreferenceUnavailableException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.measureTime

class CustomerAuthNotificationPreferenceProviderTest {
	private val customerId = UUID.fromString("3cb8c6a5-0182-45ed-a406-d191cd0ef512")

	@Test
	fun `reads notification preference using the internal contract`() {
		val builder = RestClient.builder()
			.baseUrl("http://customer-auth.test")
			.defaultHeader("X-Internal-Api-Key", "test-internal-key")
		val server = MockRestServiceServer.bindTo(builder).build()
		server.expect(requestTo("http://customer-auth.test/internal/customers/$customerId/notification-preference"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Internal-Api-Key", "test-internal-key"))
			.andRespond(
				withSuccess(
					"""
					{
					  "customerId": "$customerId",
					  "channel": "TELEGRAM",
					  "email": "customer@example.com",
					  "telegramChatId": "12345"
					}
					""".trimIndent(),
					MediaType.APPLICATION_JSON,
				),
			)
		val provider = provider(builder.build(), maxAttempts = 1)

		val preference = provider.get(customerId)

		assertEquals(customerId, preference.customerId)
		assertEquals(NotificationChannel.TELEGRAM, preference.channel)
		assertEquals("customer@example.com", preference.email)
		assertEquals("12345", preference.telegramChatId)
		server.verify()
	}

	@Test
	fun `retries temporary server failures a bounded number of times`() {
		val builder = RestClient.builder().baseUrl("http://customer-auth.test")
		val server = MockRestServiceServer.bindTo(builder).build()
		server.expect(
			ExpectedCount.times(3),
			requestTo("http://customer-auth.test/internal/customers/$customerId/notification-preference"),
		).andRespond(withServerError())
		val provider = provider(builder.build(), maxAttempts = 3)

		assertFailsWith<NotificationPreferenceUnavailableException> { provider.get(customerId) }
		server.verify()
	}

	@Test
	fun `opens circuit after the configured failure threshold`() {
		val builder = RestClient.builder().baseUrl("http://customer-auth.test")
		val server = MockRestServiceServer.bindTo(builder).build()
		server.expect(
			ExpectedCount.times(2),
			requestTo("http://customer-auth.test/internal/customers/$customerId/notification-preference"),
		).andRespond(withServerError())
		val circuitBreaker = CircuitBreaker.of(
			"customer-auth-open-test",
			CircuitBreakerConfig.custom()
				.minimumNumberOfCalls(2)
				.slidingWindowSize(2)
				.failureRateThreshold(50f)
				.build(),
		)
		val provider = provider(builder.build(), maxAttempts = 1, circuitBreaker = circuitBreaker)

		repeat(3) {
			assertFailsWith<NotificationPreferenceUnavailableException> { provider.get(customerId) }
		}

		assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
		server.verify()
	}

	@Test
	fun `stops waiting when customer auth exceeds read timeout`() {
		val executor = Executors.newSingleThreadExecutor()
		val server = HttpServer.create(InetSocketAddress(0), 0).apply {
			createContext("/internal/customers/$customerId/notification-preference") { exchange ->
				Thread.sleep(500)
				exchange.sendResponseHeaders(200, 0)
				exchange.responseBody.close()
			}
			this.executor = executor
			start()
		}
		try {
			val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(50)).build()
			val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
				setReadTimeout(Duration.ofMillis(50))
			}
			val restClient = RestClient.builder()
				.baseUrl("http://localhost:${server.address.port}")
				.requestFactory(requestFactory)
				.build()
			val provider = provider(restClient, maxAttempts = 1)

			val elapsed = measureTime {
				assertFailsWith<NotificationPreferenceUnavailableException> { provider.get(customerId) }
			}

			assertTrue(elapsed < kotlin.time.Duration.parse("1s"))
		} finally {
			server.stop(0)
			executor.shutdownNow()
		}
	}

	private fun provider(
		restClient: RestClient,
		maxAttempts: Int,
		circuitBreaker: CircuitBreaker = defaultCircuitBreaker(),
	): CustomerAuthNotificationPreferenceProvider {
		val retry = Retry.of(
			"customer-auth-test",
			RetryConfig.custom<Any>()
				.maxAttempts(maxAttempts)
				.waitDuration(Duration.ZERO)
				.retryExceptions(NotificationPreferenceUnavailableException::class.java)
				.build(),
		)
		return CustomerAuthNotificationPreferenceProvider(restClient, retry, circuitBreaker)
	}

	private fun defaultCircuitBreaker(): CircuitBreaker = CircuitBreaker.of(
			"customer-auth-test",
			CircuitBreakerConfig.custom()
				.minimumNumberOfCalls(100)
				.slidingWindowSize(100)
				.build(),
		)
}
