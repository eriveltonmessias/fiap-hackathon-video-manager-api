package com.fiap.hackathon.videomanagerapi.application.notification

import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingFailed
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingRepository
import com.fiap.hackathon.videomanagerapi.domain.video.FailureReason
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotifyVideoProcessingFailureTest {
	private val occurredAt = Instant.parse("2026-07-22T18:00:00Z")
	private val video = failedVideo()
	private val event = VideoProcessingFailed(
		eventId = UUID.fromString("3633c8f3-06ed-4d19-bf54-eb564f81171d"),
		eventType = VideoProcessingFailed.EVENT_TYPE,
		occurredAt = occurredAt,
		videoId = video.id,
		failureReason = "Unsupported codec",
	)

	@Test
	fun `uses the configured notification channel`() {
		val emailSender = RecordingSender(NotificationChannel.EMAIL)
		val telegramSender = RecordingSender(NotificationChannel.TELEGRAM)
		val recorder = RecordingFailureRecorder()
		val useCase = useCase(
			provider = NotificationPreferenceProvider {
				NotificationPreference(video.customerId, NotificationChannel.TELEGRAM, "customer@example.com", "12345")
			},
			senders = listOf(emailSender, telegramSender),
			recorder = recorder,
		)

		assertEquals(NotificationResult.SENT, useCase.execute(event))
		assertNull(emailSender.message)
		val message = assertNotNull(telegramSender.message)
		assertEquals(video.id, message.videoId)
		assertEquals("lesson.mp4", message.originalFilename)
		assertEquals("Unsupported codec", message.failureReason)
		assertNull(recorder.failure)
	}

	@Test
	fun `records unavailable customer preference without changing video status`() {
		val recorder = RecordingFailureRecorder()
		val useCase = useCase(
			provider = NotificationPreferenceProvider { throw NotificationPreferenceUnavailableException() },
			senders = emptyList(),
			recorder = recorder,
		)

		assertEquals(NotificationResult.FAILED, useCase.execute(event))
		assertEquals(VideoStatus.FAILED, video.status)
		val failure = assertNotNull(recorder.failure)
		assertEquals(event.eventId, failure.eventId)
		assertEquals(video.customerId, failure.customerId)
		assertNull(failure.channel)
		assertEquals("Customer notification preference is unavailable", failure.reason)
	}

	@Test
	fun `records a definitive delivery failure with its channel`() {
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
		assertEquals(NotificationChannel.EMAIL, failure.channel)
		assertEquals("EMAIL notification failed", failure.reason)
		assertEquals(occurredAt.plusSeconds(10), failure.failedAt)
	}

	private fun useCase(
		provider: NotificationPreferenceProvider,
		senders: List<FailureNotificationSender>,
		recorder: NotificationFailureRecorder,
	): NotifyVideoProcessingFailure = NotifyVideoProcessingFailure(
		repository = StubVideoProcessingRepository(video),
		preferenceProvider = provider,
		senders = senders,
		failureRecorder = recorder,
		clock = Clock.fixed(occurredAt.plusSeconds(10), ZoneOffset.UTC),
	)

	private fun failedVideo(): VideoProcessing {
		val videoId = UUID.fromString("179ab5b6-2813-45a7-bdf7-f6a30c99aa8f")
		val customerId = UUID.fromString("950e1a65-eb7f-47b0-98d5-e6351cbc0e6f")
		return VideoProcessing.receive(
			id = videoId,
			customerId = customerId,
			originalFilename = OriginalFilename.of("lesson.mp4"),
			receivedAt = occurredAt.minusSeconds(10),
		).apply {
			markStored(ObjectKey.of("customers/$customerId/videos/$videoId/input.mp4"), occurredAt.minusSeconds(9))
			markPendingProcessing(occurredAt.minusSeconds(8))
			markFailed(FailureReason.of("Unsupported codec"), occurredAt)
		}
	}

	private class RecordingSender(
		override val channel: NotificationChannel,
		private val shouldFail: Boolean = false,
	) : FailureNotificationSender {
		var message: FailureNotificationMessage? = null

		override fun send(preference: NotificationPreference, message: FailureNotificationMessage) {
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
