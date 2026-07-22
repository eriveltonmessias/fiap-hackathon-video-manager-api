package com.fiap.hackathon.videomanagerapi.infrastructure.observability

import com.fiap.hackathon.videomanagerapi.application.observability.VideoLifecycleObserver
import com.fiap.hackathon.videomanagerapi.application.observability.observeSafely
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsAndLoggingVideoLifecycleObserverTest {
	private val customerId = UUID.fromString("8d502fa4-ff61-4efb-9ad9-74684ac916e4")
	private val videoId = UUID.fromString("67570e88-f66f-4ab0-9b90-42a7c78a2f69")
	private val eventId = UUID.fromString("b78e5471-dabf-45df-a461-40e44d676c39")

	@Test
	fun `isolates the business flow from observer failures`() {
		val failingObserver = object : VideoLifecycleObserver {
			override fun processingResultReceived(eventId: UUID, videoId: UUID, eventType: String) {
				throw IllegalStateException("Metrics backend unavailable")
			}
		}

		failingObserver.observeSafely { processingResultReceived(eventId, videoId, "VideoProcessed") }
	}

	@Test
	fun `records lifecycle metrics without identifiers as tags`() {
		val registry = SimpleMeterRegistry()
		val observer = MetricsAndLoggingVideoLifecycleObserver(registry)

		observer.videoUploadAccepted(customerId, videoId, eventId, 2048)
		observer.processingResultHandled(eventId, videoId, "VideoProcessed", "PROCESSED")
		observer.notificationCompleted(customerId, videoId, eventId, "EMAIL", "SENT")

		assertEquals(1.0, registry.get("video.manager.uploads").counter().count())
		assertEquals(2048.0, registry.get("video.manager.upload.size").summary().totalAmount())
		assertEquals(1.0, registry.get("video.manager.processing.results").counter().count())
		assertEquals(1.0, registry.get("video.manager.notifications").counter().count())
		val tagValues = registry.meters.flatMap { meter -> meter.id.tags.map { it.value } }
		assertFalse(customerId.toString() in tagValues)
		assertFalse(videoId.toString() in tagValues)
		assertFalse(eventId.toString() in tagValues)
	}

	@Test
	fun `adds correlation identifiers to logs without exposing exception messages`() {
		val logger = LoggerFactory.getLogger(MetricsAndLoggingVideoLifecycleObserver::class.java) as Logger
		val appender = ListAppender<ILoggingEvent>().apply { start() }
		logger.addAppender(appender)
		try {
			val observer = MetricsAndLoggingVideoLifecycleObserver(SimpleMeterRegistry())
			observer.videoUploadAccepted(customerId, videoId, eventId, 1024)
			observer.outboxPublishFailed(
				eventId,
				videoId.toString(),
				"video.processing.requested",
				1,
				IllegalStateException("token=must-not-appear"),
			)

			val uploadFields = appender.list.first().keyValuePairs.associate { it.key to it.value.toString() }
			assertEquals(customerId.toString(), uploadFields["customerId"])
			assertEquals(videoId.toString(), uploadFields["videoId"])
			assertEquals(eventId.toString(), uploadFields["eventId"])
			assertTrue(appender.list.none { it.formattedMessage.contains("must-not-appear") })
			assertEquals("IllegalStateException", appender.list.last().keyValuePairs.single { it.key == "errorType" }.value)
		} finally {
			logger.detachAppender(appender)
			appender.stop()
		}
	}
}
