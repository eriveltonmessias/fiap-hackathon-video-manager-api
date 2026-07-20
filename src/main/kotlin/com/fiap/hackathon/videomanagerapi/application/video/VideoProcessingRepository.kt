package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing
import java.util.UUID

interface VideoProcessingRepository {
	fun save(videoProcessing: VideoProcessing): VideoProcessing
	fun findById(id: UUID): VideoProcessing?
}
