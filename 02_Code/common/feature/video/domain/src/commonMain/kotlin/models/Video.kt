package com.eventmap.video.models

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: String,
    val eventId: String,
    val eventTitle: String = "",
    val authorName: String = "",
    val caption: String = "",
    val thumb: String? = null,
    val viewers: Int = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    val category: String = "",
)
