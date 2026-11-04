package com.eventmap.event.data.ktor

import kotlinx.serialization.Serializable

// Контракт §8 storage (бэк построен 18.06). Зеркало server/presentation/dto/Dto.kt.

// kind: "image" | "video"; contentType из белого списка бэка (image/jpeg…, video/mp4…).
@Serializable
data class PresignRequest(
    val kind: String,
    val contentType: String,
    val eventId: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class PresignResponse(
    val mediaId: String,
    val uploadUrl: String,   // presigned PUT в R2/S3 (SigV4 в query)
    val publicUrl: String,   // постоянный URL объекта
    val provider: String = "storage",
)

@Serializable
data class ConfirmMediaRequest(
    val mediaId: String,
    val url: String,
    val contentType: String,
    val kind: String,
    val eventId: String? = null,
)

// Ответ /media/confirm и /events/{id}/media.
@Serializable
data class MediaDto(
    val id: String,
    val url: String,
    val contentType: String = "",
    val kind: String = "",
    val eventId: String? = null,
    val createdAt: Long = 0,
    val provider: String = "storage",
    val status: String = "ready",
    val thumbnailUrl: String? = null,
)

// Тело POST /videos — публикация рилса после загрузки файла.
@Serializable
data class CreateVideoRequest(
    val mediaUrl: String,
    val eventId: String? = null,
    val thumb: String? = null,
    val caption: String = "",
    val category: String = "",
    val mediaId: String? = null,
)
