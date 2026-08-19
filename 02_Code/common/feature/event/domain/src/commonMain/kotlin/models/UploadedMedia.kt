package com.eventmap.event.models

/** Результат загрузки медиа в object storage: id записи + постоянный URL объекта. */
data class UploadedMedia(
    val mediaId: String,
    val url: String,
)
