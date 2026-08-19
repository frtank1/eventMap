package com.eventmap.video.data.ktor

import kotlinx.serialization.Serializable

// Зеркала backend-DTO для загрузки медиа и публикации рилса (см. 04_Backend §8 storage).

@Serializable
data class PresignRequest(
    val kind: String,          // "image" | "video"
    val contentType: String,   // напр. "video/mp4"
)

@Serializable
data class PresignResponse(
    val mediaId: String,
    val uploadUrl: String,     // временная ссылка для прямого PUT в storage
    val publicUrl: String,     // публичная ссылка на файл после загрузки
)

@Serializable
data class ConfirmMediaRequest(
    val mediaId: String,
    val url: String,
    val contentType: String,
    val kind: String,
    val eventId: String? = null,
)

@Serializable
data class CreateVideoRequest(
    val mediaUrl: String,
    val eventId: String? = null,
    val thumb: String? = null,
    val caption: String = "",
    val category: String = "",
)
