package com.eventmap.video

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.models.VideoItem

interface VideoRepository {
    suspend fun now(): KtorResponse<List<VideoItem>>
    suspend fun react(videoId: String): KtorResponse<Unit>
}
