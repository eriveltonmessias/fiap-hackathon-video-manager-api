package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import com.fiap.hackathon.videomanagerapi.application.video.HandleVideoProcessingResult
import com.fiap.hackathon.videomanagerapi.application.video.HandlingResult
import com.fiap.hackathon.videomanagerapi.application.video.VideoProcessingResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalVideoProcessingResultHandler(
	private val handler: HandleVideoProcessingResult,
) {
	@Transactional
	fun handle(event: VideoProcessingResult): HandlingResult = handler.handle(event)
}
