package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.NotificationChannel
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreference
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreferenceNotFoundException
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreferenceProvider
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreferenceUnavailableException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.retry.Retry
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

data class NotificationPreferenceResponse(
	val customerId: UUID,
	val channel: NotificationChannel,
	val email: String,
	val telegramChatId: String?,
)

class CustomerAuthNotificationPreferenceProvider(
	private val restClient: RestClient,
	private val retry: Retry,
	private val circuitBreaker: CircuitBreaker,
) : NotificationPreferenceProvider {
	override fun get(customerId: UUID): NotificationPreference = try {
		retry.executeSupplier {
			circuitBreaker.executeSupplier { requestPreference(customerId) }
		}
	} catch (exception: CallNotPermittedException) {
		throw NotificationPreferenceUnavailableException(exception)
	}

	private fun requestPreference(customerId: UUID): NotificationPreference {
		try {
			val response = restClient.get()
				.uri("/internal/customers/{customerId}/notification-preference", customerId)
				.retrieve()
				.body(NotificationPreferenceResponse::class.java)
				?: throw NotificationPreferenceUnavailableException()
			check(response.customerId == customerId) { "Customer auth returned a different customerId" }
			return NotificationPreference(
				customerId = response.customerId,
				channel = response.channel,
				email = response.email,
				telegramChatId = response.telegramChatId,
			)
		} catch (exception: RestClientResponseException) {
			if (exception.statusCode.value() == 404) throw NotificationPreferenceNotFoundException()
			throw NotificationPreferenceUnavailableException(exception)
		} catch (exception: RestClientException) {
			throw NotificationPreferenceUnavailableException(exception)
		}
	}
}
