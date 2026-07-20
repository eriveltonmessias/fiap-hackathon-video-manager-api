package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UploadVideoTest {
	private val customerId = UUID.fromString("1d3dd58c-9f3e-4bb8-81da-591c2a02e53f")
	private val videoId = UUID.fromString("3386a249-40d8-44e8-a1d3-b7dc92bd96f1")
	private val occurredAt = Instant.parse("2026-07-20T15:00:00Z")
	private val eventId = UUID.fromString("b85c031f-29dd-49fd-9788-adeb0f70eb72")
	private val registration = RecordingVideoProcessingRequestRegistration()
	private val storage = RecordingVideoStorage()
	private val useCase = UploadVideo(
		authenticatedCustomerProvider = AuthenticatedCustomerProvider { customerId },
		registration = registration,
		storage = storage,
		policy = VideoUploadPolicy(
			maximumContentLength = 10,
			allowedContentTypes = setOf("video/mp4"),
			allowedExtensions = setOf("mp4"),
		),
		clock = Clock.fixed(occurredAt, ZoneOffset.UTC),
		idGenerator = { videoId },
		eventIdGenerator = { eventId },
	)

	@Test
	fun `uploads video before persisting pending aggregate and event`() {
		val content = "video".toByteArray()

		val result = useCase.execute(command(content = content))

		assertEquals(videoId, result.videoId)
		assertEquals(VideoStatus.PENDING_PROCESSING, result.status)
		assertContentEquals(content, storage.uploadedContent)
		assertEquals("video/mp4", storage.contentType)
		val saved = requireNotNull(registration.saved)
		assertEquals(customerId, saved.customerId)
		assertEquals("sample.mp4", saved.originalFilename.value)
		assertEquals("customers/$customerId/videos/$videoId/input", saved.inputObjectKey?.value)
		assertEquals(occurredAt, saved.createdAt)
		val event = requireNotNull(registration.event)
		assertEquals(eventId, event.eventId)
		assertEquals(videoId, event.videoId)
		assertEquals(customerId, event.customerId)
		assertEquals(saved.inputObjectKey?.value, event.inputObjectKey)
	}

	@Test
	fun `does not persist aggregate when storage fails`() {
		storage.uploadFailure = IllegalStateException("storage unavailable")

		assertFailsWith<IllegalStateException> {
			useCase.execute(command())
		}
		assertNull(registration.saved)
	}

	@Test
	fun `rejects empty video`() {
		assertFailsWith<InvalidVideoUploadException> {
			useCase.execute(command(content = byteArrayOf()))
		}
		assertNull(storage.uploadedContent)
	}

	@Test
	fun `rejects video larger than configured limit`() {
		assertFailsWith<VideoTooLargeException> {
			useCase.execute(command(content = ByteArray(11)))
		}
		assertNull(storage.uploadedContent)
	}

	@Test
	fun `rejects unsupported filename and content type`() {
		assertFailsWith<InvalidVideoUploadException> {
			useCase.execute(command(filename = "sample.txt", contentType = "text/plain"))
		}
		assertNull(storage.uploadedContent)
	}

	private fun command(
		filename: String = "sample.mp4",
		contentType: String = "video/mp4",
		content: ByteArray = "video".toByteArray(),
	) = UploadVideoCommand(
		originalFilename = filename,
		contentType = contentType,
		contentLength = content.size.toLong(),
		content = ByteArrayInputStream(content),
	)
}

private class RecordingVideoProcessingRequestRegistration : VideoProcessingRequestRegistration {
	var saved: VideoProcessing? = null
	var event: VideoProcessingRequested? = null

	override fun save(videoProcessing: VideoProcessing, event: VideoProcessingRequested): VideoProcessing =
		videoProcessing.also {
			saved = it
			this.event = event
		}
}

private class RecordingVideoStorage : VideoStorage {
	var uploadedContent: ByteArray? = null
	var contentType: String? = null
	var uploadFailure: RuntimeException? = null

	override fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String?,
	) {
		uploadFailure?.let { throw it }
		this.uploadedContent = content.readAllBytes()
		this.contentType = contentType
	}

	override fun exists(bucket: StorageBucket, objectKey: ObjectKey): Boolean = false
	override fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream = error("not used")
}
