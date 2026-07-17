package com.fiap.hackathon.videomanagerapi.domain.video

import java.time.Instant

class VideoProcessing private constructor(
	val id: VideoId,
	val customerId: CustomerId,
	val originalFilename: OriginalFilename,
	status: VideoStatus,
	inputObjectKey: ObjectKey?,
	outputObjectKey: ObjectKey?,
	failureReason: FailureReason?,
	val createdAt: Instant,
	updatedAt: Instant,
) {
	var status: VideoStatus = status
		private set

	var inputObjectKey: ObjectKey? = inputObjectKey
		private set

	var outputObjectKey: ObjectKey? = outputObjectKey
		private set

	var failureReason: FailureReason? = failureReason
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
		validateSnapshot(status, inputObjectKey, outputObjectKey, failureReason)
	}

	fun markStored(objectKey: ObjectKey, changedAt: Instant) {
		ensureStatus(VideoStatus.RECEIVED)
		ensureChangedAt(changedAt)
		inputObjectKey = objectKey
		status = VideoStatus.STORED
		updatedAt = changedAt
	}

	fun markPendingProcessing(changedAt: Instant) {
		ensureStatus(VideoStatus.STORED)
		ensureChangedAt(changedAt)
		status = VideoStatus.PENDING_PROCESSING
		updatedAt = changedAt
	}

	fun markProcessing(changedAt: Instant) {
		ensureStatus(VideoStatus.PENDING_PROCESSING)
		ensureChangedAt(changedAt)
		status = VideoStatus.PROCESSING
		updatedAt = changedAt
	}

	fun markProcessed(objectKey: ObjectKey, changedAt: Instant) {
		ensureStatus(VideoStatus.PROCESSING)
		ensureChangedAt(changedAt)
		outputObjectKey = objectKey
		status = VideoStatus.PROCESSED
		updatedAt = changedAt
	}

	fun markFailed(reason: FailureReason, changedAt: Instant) {
		check(status != VideoStatus.PROCESSED && status != VideoStatus.FAILED) {
			"Video in status $status cannot transition to FAILED"
		}
		ensureChangedAt(changedAt)
		failureReason = reason
		status = VideoStatus.FAILED
		updatedAt = changedAt
	}

	fun isTerminal(): Boolean = status == VideoStatus.PROCESSED || status == VideoStatus.FAILED

	private fun ensureStatus(expected: VideoStatus) {
		check(status == expected) { "Video in status $status cannot transition from expected status $expected" }
	}

	private fun ensureChangedAt(changedAt: Instant) {
		require(!changedAt.isBefore(updatedAt)) { "changedAt must not be before updatedAt" }
	}

	companion object {
		fun receive(
			id: VideoId,
			customerId: CustomerId,
			originalFilename: OriginalFilename,
			receivedAt: Instant,
		): VideoProcessing = VideoProcessing(
			id = id,
			customerId = customerId,
			originalFilename = originalFilename,
			status = VideoStatus.RECEIVED,
			inputObjectKey = null,
			outputObjectKey = null,
			failureReason = null,
			createdAt = receivedAt,
			updatedAt = receivedAt,
		)

		fun restore(
			id: VideoId,
			customerId: CustomerId,
			originalFilename: OriginalFilename,
			status: VideoStatus,
			inputObjectKey: ObjectKey?,
			outputObjectKey: ObjectKey?,
			failureReason: FailureReason?,
			createdAt: Instant,
			updatedAt: Instant,
		): VideoProcessing = VideoProcessing(
			id = id,
			customerId = customerId,
			originalFilename = originalFilename,
			status = status,
			inputObjectKey = inputObjectKey,
			outputObjectKey = outputObjectKey,
			failureReason = failureReason,
			createdAt = createdAt,
			updatedAt = updatedAt,
		)

		private fun validateSnapshot(
			status: VideoStatus,
			inputObjectKey: ObjectKey?,
			outputObjectKey: ObjectKey?,
			failureReason: FailureReason?,
		) {
			when (status) {
				VideoStatus.RECEIVED -> {
					require(inputObjectKey == null) { "RECEIVED video must not have an input object key" }
					require(outputObjectKey == null) { "RECEIVED video must not have an output object key" }
					require(failureReason == null) { "RECEIVED video must not have a failure reason" }
				}

				VideoStatus.STORED,
				VideoStatus.PENDING_PROCESSING,
				VideoStatus.PROCESSING,
				-> {
					require(inputObjectKey != null) { "$status video must have an input object key" }
					require(outputObjectKey == null) { "$status video must not have an output object key" }
					require(failureReason == null) { "$status video must not have a failure reason" }
				}

				VideoStatus.PROCESSED -> {
					require(inputObjectKey != null) { "PROCESSED video must have an input object key" }
					require(outputObjectKey != null) { "PROCESSED video must have an output object key" }
					require(failureReason == null) { "PROCESSED video must not have a failure reason" }
				}

				VideoStatus.FAILED -> {
					require(outputObjectKey == null) { "FAILED video must not have an output object key" }
					require(failureReason != null) { "FAILED video must have a failure reason" }
				}
			}
		}
	}
}
