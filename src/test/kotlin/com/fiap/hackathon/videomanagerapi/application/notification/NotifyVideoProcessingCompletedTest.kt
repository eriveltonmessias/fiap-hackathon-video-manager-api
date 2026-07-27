package com.fiap.hackathon.videomanagerapi.application.notification

import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotifyVideoProcessingCompletedTest {
	private val occurredAt = Instant.parse("2026-07-27T18:00:00Z")
	private val video = processedVideo()
	private val event = VideoProcessed(
		eventId = UUID.fromString("ba97dfd6-bb80-4a15-a373-3c5d828099cc"),
		eventType = VideoProcessed.EVENT_TYPE,
		occurredAt = occurredAt,
		videoId = video.id,
		outputObjectKey = checkNotNull(video.outputObjectKey).value,
	)

	@Test
	fun `sends the authenticated Kong download link through the preferred channel`() {
		val emailSender = RecordingSender(NotificationChannel.EMAIL)
		val telegramSender = RecordingSender(NotificationChannel.TELEGRAM)
		val recorder = RecordingFailureRecorder()
		val useCase = useCase(
			provider = NotificationPreferenceProvider {
				NotificationPreference(video.customerId, NotificationChannel.EMAIL, "customer@example.com", null)
			},
			senders = listOf(emailSender, telegramSender),
			recorder = recorder,
		)

		assertEquals(NotificationResult.SENT, useCase.execute(event))
		val message = assertNotNull(emailSender.message)
		assertEquals(video.id, message.videoId)
		assertEquals("lesson.mp4", message.originalFilename)
		assertEquals(
			"https://api.fiapx.example/videos/${video.id}/download",
			message.downloadUrl,
		)
		assertNull(telegramSender.message)
		assertNull(recorder.failure)
	}

	@Test
	fun `records an email delivery failure without changing the processed video`() {
		val recorder = RecordingFailureRecorder()
		val sender = RecordingSender(NotificationChannel.EMAIL, shouldFail = true)
		val useCase = useCase(
			provider = NotificationPreferenceProvider {
				NotificationPreference(video.customerId, NotificationChannel.EMAIL, "customer@example.com", null)
			},
			senders = listOf(sender),
			recorder = recorder,
		)

		assertEquals(NotificationResult.FAILED, useCase.execute(event))
		val failure = assertNotNull(recorder.failure)
		assertEquals(event.eventId, failure.eventId)
		assertEquals(video.customerId, failure.customerId)
		assertEquals(NotificationChannel.EMAIL, failure.channel)
		assertEquals("EMAIL notification failed", failure.reason)
		assertEquals(occurredAt.plusSeconds(10), failure.failedAt)
	}

	private fun useCase(
		provider: NotificationPreferenceProvider,
		senders: List<ProcessingCompletedNotificationSender>,
		recorder: NotificationFailureRecorder,
	): NotifyVideoProcessingCompleted = NotifyVideoProcessingCompleted(
		repository = StubVideoProcessingRepository(video),
		preferenceProvider = provider,
		senders = senders,
		failureRecorder = recorder,
		publicBaseUrl = "https://api.fiapx.example/",
		clock = Clock.fixed(occurredAt.plusSeconds(10), ZoneOffset.UTC),
	)

	private fun processedVideo(): VideoProcessing {
		val videoId = UUID.fromString("78a5fdbf-b771-4117-82b6-76de9f8633bb")
		val customerId = UUID.fromString("60beb76f-7d6e-4f3f-b2ae-36aa5091bc80")
		return VideoProcessing.receive(
			id = videoId,
			customerId = customerId,
			originalFilename = OriginalFilename.of("lesson.mp4"),
			receivedAt = occurredAt.minusSeconds(10),
		).apply {
			markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input.mp4"), occurredAt.minusSeconds(9))
			markPendingProcessing(occurredAt.minusSeconds(8))
			markProcessed(
				ObjectKey.of("customers/$customerId/videos/$videoId/frames.zip"),
				occurredAt,
			)
		}
	}

	private class RecordingSender(
		override val channel: NotificationChannel,
		private val shouldFail: Boolean = false,
	) : ProcessingCompletedNotificationSender {
		var message: ProcessingCompletedNotificationMessage? = null

		override fun send(preference: NotificationPreference, message: ProcessingCompletedNotificationMessage) {
			this.message = message
			if (shouldFail) throw NotificationDeliveryException("$channel notification failed")
		}
	}

	private class RecordingFailureRecorder : NotificationFailureRecorder {
		var failure: NotificationFailure? = null
		override fun record(failure: NotificationFailure) {
			this.failure = failure
		}
	}

	private class StubVideoProcessingRepository(
		private val video: VideoProcessing,
	) : VideoProcessingRepository {
		override fun save(videoProcessing: VideoProcessing): VideoProcessing = videoProcessing
		override fun findById(id: UUID): VideoProcessing? = video.takeIf { it.id == id }
		override fun findByIdForUpdate(id: UUID): VideoProcessing? = findById(id)
	}
}
