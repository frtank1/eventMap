package com.eventmap.server.domain.usecase

import com.eventmap.server.config.AppConfig
import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.model.Video
import com.eventmap.server.domain.repository.*
import java.util.UUID

class MuxVideoService(
    private val config: AppConfig,
    private val gateway: MuxGateway,
    private val uploads: MuxUploadRepository,
    private val media: MediaRepository,
    private val access: MediaAccess,
) {
    val enabled get() = config.videoProvider == "mux"
    data class Upload(val mediaId: String, val uploadUrl: String)

    suspend fun begin(owner: String, eventId: String?, type: String, size: Long?): Upload {
        if (!enabled || !gateway.configured) throw MediaFailure(503, "VIDEO_OFF", "Видеосервис не настроен")
        if (type !in setOf("video/mp4", "video/quicktime", "video/webm") || size == null || size !in 1..(64L * 1024 * 1024))
            throw MediaFailure(422, "BAD_REQUEST", "Выберите видео до 64 МБ")
        requireEvent(eventId, owner)
        val id = "mux-${UUID.randomUUID()}"
        uploads.reserve(id, owner, eventId!!, type, config.muxMaxUploadsPerDay)
        // Persist before calling Mux: a fast webhook can arrive before the HTTP response.
        val remote = gateway.createUpload(id)
        uploads.attachUpload(id, remote.id)
        return Upload(id, remote.url)
    }

    suspend fun confirm(id: String, owner: String, eventId: String?, type: String): Media {
        requireEvent(eventId, owner)
        return gateway.playback(uploads.confirm(id, owner, eventId!!, type))
    }

    suspend fun publish(id: String, owner: String, eventId: String?, caption: String): Video {
        requireEvent(eventId, owner)
        if (caption.length > 500) throw MediaFailure(422, "BAD_REQUEST", "Подпись не длиннее 500 символов")
        return decorate(uploads.publish(id, owner, eventId!!, caption.trim()))
    }

    suspend fun get(id: String, viewer: String?): Media {
        val item = media.byId(id) ?: throw MediaFailure(404, "MEDIA_NOT_FOUND", "Медиа не найдено")
        // Once attached, access follows the event, even if the uploader later loses their invitation.
        if (item.eventId != null) requireEvent(item.eventId, viewer)
        else if (viewer != item.ownerUserId) throw MediaFailure(403, "MEDIA_FORBIDDEN", "Нет доступа к медиа")
        return gateway.playback(item)
    }

    suspend fun eventMedia(eventId: String, viewer: String?): List<Media> {
        requireEvent(eventId, viewer)
        return media.byEvent(eventId).filter { it.status != "uploading" || it.ownerUserId == viewer }
            .map(gateway::playback)
    }

    suspend fun decorate(video: Video): Video {
        val id = video.mediaId ?: return video
        val m = media.byId(id) ?: return video.copy(mediaUrl = "", thumb = null, status = "errored")
        val playable = gateway.playback(m)
        return video.copy(mediaUrl = playable.url, thumb = playable.thumbnailUrl, status = playable.status)
    }

    suspend fun webhook(body: ByteArray, signature: String?) {
        val event = gateway.verifyWebhook(body, signature)
        uploads.apply(event, config.muxMaxDurationSeconds)
    }

    private suspend fun requireEvent(id: String?, viewer: String?) {
        if (id.isNullOrBlank()) throw MediaFailure(422, "BAD_REQUEST", "Сначала выберите событие для ролика")
        if (!access.canView(id, viewer)) throw MediaFailure(403, "MEDIA_FORBIDDEN", "Событие недоступно")
    }
}
