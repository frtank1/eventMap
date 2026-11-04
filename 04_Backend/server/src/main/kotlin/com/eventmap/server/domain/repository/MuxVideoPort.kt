package com.eventmap.server.domain.repository

import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.model.Video

class MediaFailure(val status: Int, val code: String, override val message: String) : RuntimeException(message)

data class MuxUpload(val id: String, val url: String)
data class MuxEvent(
    val id: String,
    val type: String,
    val mediaId: String?,
    val uploadId: String?,
    val assetId: String?,
    val playbackId: String?,
    val duration: Double?,
)

interface MuxGateway {
    val configured: Boolean
    suspend fun createUpload(mediaId: String): MuxUpload
    fun playback(media: Media): Media
    /** Verifies raw bytes before parsing, including timestamp freshness. */
    fun verifyWebhook(body: ByteArray, signature: String?): MuxEvent
}

interface MediaAccess {
    suspend fun canView(eventId: String, userId: String?): Boolean
}

interface MuxUploadRepository {
    /** Reservation and per-owner quota check are atomic. */
    suspend fun reserve(id: String, owner: String, eventId: String, contentType: String, dailyLimit: Int)
    suspend fun attachUpload(id: String, uploadId: String)
    suspend fun confirm(id: String, owner: String, eventId: String, contentType: String): Media
    suspend fun publish(id: String, owner: String, eventId: String, caption: String): Video
    /** Deduplication and state mutation happen in a single transaction. */
    suspend fun apply(event: MuxEvent, maxDurationSeconds: Int)
}
