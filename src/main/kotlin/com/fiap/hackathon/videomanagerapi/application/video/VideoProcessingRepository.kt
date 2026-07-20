package com.fiap.hackathon.videomanagerapi.application.video

import com.fiap.hackathon.videomanagerapi.domain.video.VideoId
import com.fiap.hackathon.videomanagerapi.domain.video.VideoProcessing

interface VideoProcessingRepository {
	fun save(videoProcessing: VideoProcessing): VideoProcessing
	fun findById(id: VideoId): VideoProcessing?
}
