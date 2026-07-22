package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.FailureNotificationMessage
import com.fiap.hackathon.videomanagerapi.application.notification.FailureNotificationSender
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationChannel
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationDeliveryException
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreference
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class EmailFailureNotificationSender(
	private val mailSender: JavaMailSender,
	private val properties: NotificationProperties,
) : FailureNotificationSender {
	override val channel: NotificationChannel = NotificationChannel.EMAIL

	override fun send(preference: NotificationPreference, message: FailureNotificationMessage) {
		try {
			mailSender.send(
				SimpleMailMessage().apply {
					from = properties.emailFrom
					setTo(preference.email)
					subject = "Video processing failed"
					text = notificationText(message)
				},
			)
		} catch (exception: MailException) {
			throw NotificationDeliveryException("EMAIL notification failed", exception)
		}
	}
}

class TelegramFailureNotificationSender(
	private val restClient: RestClient,
	private val properties: NotificationProperties,
) : FailureNotificationSender {
	override val channel: NotificationChannel = NotificationChannel.TELEGRAM

	override fun send(preference: NotificationPreference, message: FailureNotificationMessage) {
		val chatId = preference.telegramChatId
		if (properties.telegramBotToken.isBlank() || chatId.isNullOrBlank()) {
			throw NotificationDeliveryException("TELEGRAM notification is not configured")
		}
		try {
			restClient.post()
				.uri("/bot{token}/sendMessage", properties.telegramBotToken)
				.body(mapOf("chat_id" to chatId, "text" to notificationText(message)))
				.retrieve()
				.toBodilessEntity()
		} catch (exception: RestClientException) {
			throw NotificationDeliveryException("TELEGRAM notification failed", exception)
		}
	}
}

private fun notificationText(message: FailureNotificationMessage): String =
	"Video ${message.originalFilename} (${message.videoId}) failed: ${message.failureReason}"
