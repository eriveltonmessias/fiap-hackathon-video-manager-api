package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import com.fiap.hackathon.videomanagerapi.domain.video.ObjectKey
import com.fiap.hackathon.videomanagerapi.domain.video.OriginalFilename
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import com.fiap.hackathon.videomanagerapi.domain.video.VideoStatus
import java.io.InputStream
import java.time.Clock
import java.util.UUID

data class UploadVideoCommand(
	val originalFilename: String?,
	val contentType: String?,
	val contentLength: Long,
	val content: InputStream,
)

data class UploadVideoResult(
	val videoId: UUID,
	val status: VideoStatus,
)

class UploadVideo(
	private val authenticatedCustomerProvider: AuthenticatedCustomerProvider,
	private val registration: VideoProcessingRequestRegistration,
	private val storage: VideoStorage,
	private val policy: VideoUploadPolicy,
	private val clock: Clock,
	private val idGenerator: () -> UUID = UUID::randomUUID,
	private val eventIdGenerator: () -> UUID = UUID::randomUUID,
	private val observer: VideoLifecycleObserver = VideoLifecycleObserver.NONE,
) {
	fun execute(command: UploadVideoCommand): UploadVideoResult {
		val filename = command.originalFilename.toOriginalFilename()
		val contentType = policy.validate(filename, command.contentLength, command.contentType)
		val customerId = authenticatedCustomerProvider.customerId()
		val videoId = idGenerator()
		val objectKey = ObjectKey.of("customers/$customerId/videos/$videoId/input")
		val occurredAt = clock.instant()
		val video = VideoProcessing.receive(videoId, customerId, filename, occurredAt)

		storage.upload(
			bucket = StorageBucket.INPUT,
			objectKey = objectKey,
			content = command.content,
			contentLength = command.contentLength,
			contentType = contentType,
		)
		video.markStored(objectKey, occurredAt)
		video.markPendingProcessing(occurredAt)
		val event = VideoProcessingRequested(
			eventId = eventIdGenerator(),
			occurredAt = occurredAt,
			videoId = videoId,
			customerId = customerId,
			originalFilename = filename.value,
			inputObjectKey = objectKey.value,
		)
		val saved = registration.save(video, event)
		observer.observeSafely { videoUploadAccepted(customerId, videoId, event.eventId, command.contentLength) }
		return UploadVideoResult(saved.id, saved.status)
	}

	private fun String?.toOriginalFilename(): OriginalFilename = try {
		OriginalFilename.of(this ?: "")
	} catch (exception: IllegalArgumentException) {
		throw InvalidVideoUploadException(exception.message ?: "Invalid video filename")
	}
}
