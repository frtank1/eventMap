package com.eventmap.video.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.video.VideoRepository
import com.eventmap.video.data.ktor.VideoRemoteDataSource
import com.eventmap.video.models.VideoItem
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class VideoRepositoryImpl(
    private val remote: VideoRemoteDataSource,
) : VideoRepository {
    override suspend fun now(): KtorResponse<List<VideoItem>> = safeRequest { remote.now() }
    override suspend fun react(videoId: String): KtorResponse<Unit> = unitRequest { remote.react(videoId) }

    private suspend inline fun unitRequest(call: () -> HttpResponse): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
