package com.eventmap.video

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.models.VideoItem

interface VideoRepository {
    suspend fun now(): KtorResponse<List<VideoItem>>
    suspend fun react(videoId: String): KtorResponse<Unit>

    /**
     * Публикует рилс: presigned-загрузка файла в storage → подтверждение → создание видео.
     * @param bytes сырые байты видеофайла (даёт реальная запись с камеры — см. RecordVideoViewModel TODO)
     * @param contentType напр. "video/mp4"
     * @param eventId привязка к событию (опционально)
     */
    suspend fun publishReel(
        bytes: ByteArray,
        contentType: String,
        eventId: String?,
        caption: String,
        onProgress: ((Float) -> Unit)? = null,
    ): KtorResponse<VideoItem>
}
