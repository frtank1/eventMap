package com.eventmap.video.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.video.VideoRepository
import com.eventmap.video.data.ktor.ConfirmMediaRequest
import com.eventmap.video.data.ktor.CreateVideoRequest
import com.eventmap.video.data.ktor.PresignRequest
import com.eventmap.video.data.ktor.PresignResponse
import com.eventmap.video.data.ktor.VideoRemoteDataSource
import com.eventmap.video.models.VideoItem
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class VideoRepositoryImpl(
    private val remote: VideoRemoteDataSource,
) : VideoRepository {
    override suspend fun now(): KtorResponse<List<VideoItem>> = safeRequest { remote.now() }
    override suspend fun react(videoId: String): KtorResponse<Unit> = unitRequest { remote.react(videoId) }

    // presigned-загрузка → подтверждение → создание рилса (см. backend §8 storage)
    override suspend fun publishReel(
        bytes: ByteArray,
        contentType: String,
        eventId: String?,
        caption: String,
    ): KtorResponse<VideoItem> {
        // 1) presigned-URL
        val presign = safeRequest<PresignResponse> { remote.presign(PresignRequest("video", contentType)) }
        val p: PresignResponse = when (presign) {
            is KtorResponse.Success -> presign.data
            is KtorResponse.Failure -> return presign
        }
        // 2) прямая загрузка файла в storage
        val put = try {
            remote.uploadBytes(p.uploadUrl, bytes, contentType)
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!put.status.isSuccess()) return KtorResponse.failure(put)
        // 3) подтвердить запись медиа
        val confirm = try {
            remote.confirmMedia(ConfirmMediaRequest(p.mediaId, p.publicUrl, contentType, "video", eventId))
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!confirm.status.isSuccess()) return KtorResponse.failure(confirm)
        // 4) создать рилс
        return safeRequest {
            remote.createVideo(CreateVideoRequest(mediaUrl = p.publicUrl, eventId = eventId, thumb = p.publicUrl, caption = caption))
        }
    }

    private suspend inline fun unitRequest(call: () -> HttpResponse): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
