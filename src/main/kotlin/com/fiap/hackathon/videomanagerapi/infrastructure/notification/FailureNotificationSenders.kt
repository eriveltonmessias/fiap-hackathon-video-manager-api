package com.fiap.hackathon.videomanagerapi.infrastructure.notification

import com.fiap.hackathon.videomanagerapi.application.notification.FailureNotificationMessage
import com.fiap.hackathon.videomanagerapi.application.notification.FailureNotificationSender
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationChannel
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationDeliveryException
import com.fiap.hackathon.videomanagerapi.application.notification.NotificationPreference
import com.fiap.hackathon.videomanagerapi.application.notification.ProcessingCompletedNotificationMessage
import com.fiap.hackathon.videomanagerapi.application.notification.ProcessingCompletedNotificationSender
import org.slf4j.LoggerFactory
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
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun send(preference: NotificationPreference, message: FailureNotificationMessage) {
		try {
			logger.atInfo()
				.addKeyValue("customerId", preference.customerId)
				.addKeyValue("videoId", message.videoId)
				.addKeyValue("recipient", preference.email)
				.addKeyValue("notificationType", "processing_failed")
				.log("Sending email notification")
			mailSender.send(
				SimpleMailMessage().apply {
					from = properties.emailFrom
					setTo(preference.email)
					subject = "Video processing failed"
					text = notificationText(message)
				},
			)
			logger.atInfo()
				.addKeyValue("customerId", preference.customerId)
				.addKeyValue("videoId", message.videoId)
				.addKeyValue("recipient", preference.email)
				.addKeyValue("notificationType", "processing_failed")
				.log("Email notification sent")
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

class EmailProcessingCompletedNotificationSender(
	private val mailSender: JavaMailSender,
	private val properties: NotificationProperties,
) : ProcessingCompletedNotificationSender {
	override val channel: NotificationChannel = NotificationChannel.EMAIL
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun send(preference: NotificationPreference, message: ProcessingCompletedNotificationMessage) {
		try {
			logger.atInfo()
				.addKeyValue("customerId", preference.customerId)
				.addKeyValue("videoId", message.videoId)
				.addKeyValue("recipient", preference.email)
				.addKeyValue("notificationType", "processing_completed")
				.log("Sending email notification")
			mailSender.send(
				SimpleMailMessage().apply {
					from = properties.emailFrom
					setTo(preference.email)
					subject = "Seu vídeo está pronto para download"
					text = completedNotificationText(message)
				},
			)
			logger.atInfo()
				.addKeyValue("customerId", preference.customerId)
				.addKeyValue("videoId", message.videoId)
				.addKeyValue("recipient", preference.email)
				.addKeyValue("notificationType", "processing_completed")
				.log("Email notification sent")
		} catch (exception: MailException) {
			throw NotificationDeliveryException("EMAIL notification failed", exception)
		}
	}
}

class TelegramProcessingCompletedNotificationSender(
	private val restClient: RestClient,
	private val properties: NotificationProperties,
) : ProcessingCompletedNotificationSender {
	override val channel: NotificationChannel = NotificationChannel.TELEGRAM

	override fun send(preference: NotificationPreference, message: ProcessingCompletedNotificationMessage) {
		val chatId = preference.telegramChatId
		if (properties.telegramBotToken.isBlank() || chatId.isNullOrBlank()) {
			throw NotificationDeliveryException("TELEGRAM notification is not configured")
		}
		try {
			restClient.post()
				.uri("/bot{token}/sendMessage", properties.telegramBotToken)
				.body(mapOf("chat_id" to chatId, "text" to completedNotificationText(message)))
				.retrieve()
				.toBodilessEntity()
		} catch (exception: RestClientException) {
			throw NotificationDeliveryException("TELEGRAM notification failed", exception)
		}
	}
}

private fun notificationText(message: FailureNotificationMessage): String =
	"Video ${message.originalFilename} (${message.videoId}) failed: ${message.failureReason}"

private fun completedNotificationText(message: ProcessingCompletedNotificationMessage): String =
	"""
	Seu vídeo ${message.originalFilename} foi processado com sucesso.

	Download: ${message.downloadUrl}

	O download exige autenticação com a sua conta FIAP X.
	""".trimIndent()
