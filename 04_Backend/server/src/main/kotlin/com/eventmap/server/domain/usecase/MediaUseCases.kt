package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.repository.MediaRepository
import com.eventmap.server.domain.repository.StoragePort
import java.util.UUID

// ===== Медиа: загрузка через presigned-URL (§8 storage) =====
//
// Флоу клиента:
//  1) POST /media/presign {kind, contentType} → {mediaId, uploadUrl, publicUrl}
//  2) HTTP PUT файла напрямую в uploadUrl (object storage)
//  3) POST /media/confirm {mediaId, url, contentType, kind, eventId?} → запись сохранена
//  4) GET /events/{id}/media → список медиа события

private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic")
private val VIDEO_TYPES = setOf("video/mp4", "video/quicktime", "video/webm")

private fun extFor(contentType: String): String? = when (contentType.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "video/mp4" -> "mp4"
    "video/quicktime" -> "mov"
    "video/webm" -> "webm"
    else -> null
}

class PresignUploadUseCase(private val storage: StoragePort) {
    sealed interface Result {
        data class Ok(val mediaId: String, val uploadUrl: String, val publicUrl: String) : Result
        data object NotConfigured : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    operator fun invoke(userId: String, kind: String, contentType: String): Result {
        if (!storage.configured) return Result.NotConfigured
        val allowed = when (kind) {
            "image" -> IMAGE_TYPES
            "video" -> VIDEO_TYPES
            else -> return Result.Rejected("BAD_KIND", "kind: image | video")
        }
        if (contentType !in allowed) {
            return Result.Rejected("BAD_TYPE", "Неподдерживаемый content-type для $kind")
        }
        val ext = extFor(contentType) ?: return Result.Rejected("BAD_TYPE", "Неподдерживаемый content-type")
        val mediaId = UUID.randomUUID().toString()
        val key = "$kind/$userId/$mediaId.$ext"
        return Result.Ok(
            mediaId = mediaId,
            uploadUrl = storage.presignPut(key, contentType),
            publicUrl = storage.publicUrl(key),
        )
    }
}

class ConfirmMediaUseCase(private val media: MediaRepository) {
    suspend operator fun invoke(
        id: String,
        userId: String,
        url: String,
        contentType: String,
        kind: String,
        eventId: String?,
    ): Media = media.create(id, userId, url, contentType, kind, eventId)
}

class GetEventMediaUseCase(private val media: MediaRepository) {
    suspend operator fun invoke(eventId: String): List<Media> = media.byEvent(eventId)
}
