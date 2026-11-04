package com.eventmap.server.data.video

import com.eventmap.server.data.db.*
import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.model.Video
import com.eventmap.server.domain.repository.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class MediaAccessImpl : MediaAccess {
    override suspend fun canView(eventId: String, userId: String?): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val event = EventsTable.selectAll().where { EventsTable.id eq eventId }.singleOrNull() ?: return@newSuspendedTransaction false
        if (event[EventsTable.visibility] == "public" && event[EventsTable.kind] != "private") return@newSuspendedTransaction true
        if (userId == null) return@newSuspendedTransaction false
        if (event[EventsTable.organizerId] == userId) return@newSuspendedTransaction true
        val approved = EventJoinRequestsTable.selectAll().where {
            (EventJoinRequestsTable.eventId eq eventId) and (EventJoinRequestsTable.userId eq userId) and
                (EventJoinRequestsTable.status eq "approved")
        }.any()
        if (approved) return@newSuspendedTransaction true
        InvitationsTable.join(InvitationGuestsTable, JoinType.INNER, InvitationsTable.id, InvitationGuestsTable.invitationId)
            .selectAll().where {
                (InvitationsTable.eventId eq eventId) and (InvitationGuestsTable.userId eq userId) and
                    ((InvitationGuestsTable.rsvp.isNull()) or (InvitationGuestsTable.rsvp neq "no"))
            }.any()
    }
}

class MuxUploadRepositoryImpl : MuxUploadRepository {
    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun reserve(id: String, owner: String, eventId: String, contentType: String, dailyLimit: Int) = tx {
        // Serialize quota reservations for one user across all server instances.
        UsersTable.selectAll().where { UsersTable.id eq owner }.forUpdate().singleOrNull()
            ?: throw MediaFailure(401, "UNAUTHORIZED", "Нужен действующий аккаунт")
        val now = System.currentTimeMillis()
        val count = MediaTable.selectAll().where {
            (MediaTable.ownerUserId eq owner) and (MediaTable.provider eq "mux") and
                (MediaTable.createdAtMs greaterEq now - 86_400_000)
        }.count()
        if (count >= dailyLimit) throw MediaFailure(429, "VIDEO_LIMIT", "Лимит загрузок за 24 часа исчерпан")
        MediaTable.insert {
            it[MediaTable.id] = id; it[ownerUserId] = owner; it[MediaTable.eventId] = eventId
            it[url] = ""; it[MediaTable.contentType] = contentType; it[kind] = "video"
            it[provider] = "mux"; it[status] = "uploading"; it[createdAtMs] = now
        }
        MuxUploadsTable.insert { it[mediaId] = id; it[createdAtMs] = now }
        Unit
    }

    override suspend fun attachUpload(id: String, uploadId: String) = tx {
        MuxUploadsTable.update({ MuxUploadsTable.mediaId eq id }) { it[MuxUploadsTable.uploadId] = uploadId }
        Unit
    }

    override suspend fun confirm(id: String, owner: String, eventId: String, contentType: String): Media = tx {
        val row = owned(id, owner, eventId)
        if (row[MediaTable.contentType] != contentType) throw MediaFailure(422, "BAD_REQUEST", "Тип файла не совпадает")
        MuxUploadsTable.update({ MuxUploadsTable.mediaId eq id }) { it[confirmed] = true }
        MediaTable.update({ (MediaTable.id eq id) and (MediaTable.status eq "uploading") }) { it[status] = "processing" }
        media(MediaTable.selectAll().where { MediaTable.id eq id }.single())
    }

    override suspend fun publish(id: String, owner: String, eventId: String, caption: String): Video = tx {
        val row = owned(id, owner, eventId)
        val upload = MuxUploadsTable.selectAll().where { MuxUploadsTable.mediaId eq id }.single()
        if (!upload[MuxUploadsTable.confirmed]) throw MediaFailure(409, "UPLOAD_INCOMPLETE", "Сначала завершите загрузку")
        if (row[MediaTable.status] == "errored") throw MediaFailure(422, "VIDEO_ERRORED", "Не удалось обработать ролик. Загрузите другой файл")
        val event = EventsTable.selectAll().where { EventsTable.id eq eventId }.singleOrNull()
            ?: throw MediaFailure(404, "EVENT_NOT_FOUND", "Событие не найдено")
        // The media row lock makes retries/concurrent publish requests idempotent.
        VideosTable.insertIgnore {
            it[VideosTable.id] = id; it[authorUserId] = owner; it[VideosTable.eventId] = eventId
            it[mediaId] = id; it[mediaUrl] = ""; it[VideosTable.caption] = caption
            it[category] = event[EventsTable.category]; it[createdAtMs] = System.currentTimeMillis()
        }
        val published = VideosTable.selectAll().where { VideosTable.id eq id }.single()
        Video(id = id, eventId = eventId, eventTitle = event[EventsTable.title], caption = published[VideosTable.caption],
            mediaUrl = "", category = event[EventsTable.category], status = row[MediaTable.status], mediaId = id)
    }

    private fun owned(id: String, owner: String, eventId: String): ResultRow {
        val row = MediaTable.selectAll().where { MediaTable.id eq id }.forUpdate().singleOrNull()
            ?: throw MediaFailure(404, "MEDIA_NOT_FOUND", "Медиа не найдено")
        if (row[MediaTable.provider] != "mux" || row[MediaTable.ownerUserId] != owner || row[MediaTable.eventId] != eventId)
            throw MediaFailure(403, "MEDIA_FORBIDDEN", "Загрузка принадлежит другому автору или событию")
        return row
    }

    override suspend fun apply(event: MuxEvent, maxDurationSeconds: Int) = tx {
        val inserted = MuxWebhookEventsTable.insertIgnore {
            it[id] = event.id; it[receivedAtMs] = System.currentTimeMillis()
        }.insertedCount
        if (inserted == 0) return@tx
        val upload = MuxUploadsTable.selectAll().where {
            (MuxUploadsTable.mediaId eq (event.mediaId ?: "")) or
                (MuxUploadsTable.uploadId eq (event.uploadId ?: "")) or
                (MuxUploadsTable.assetId eq (event.assetId ?: ""))
        }.singleOrNull() ?: return@tx // Other assets in this Mux environment are intentionally ignored.
        val id = upload[MuxUploadsTable.mediaId]
        val row = MediaTable.selectAll().where { MediaTable.id eq id }.forUpdate().single()
        val knownAsset = upload[MuxUploadsTable.assetId]
        if (knownAsset != null && event.assetId != null && knownAsset != event.assetId) return@tx
        if (event.assetId != null) MuxUploadsTable.update({ MuxUploadsTable.mediaId eq id }) { it[assetId] = event.assetId }
        val next = muxNextStatus(row[MediaTable.status], event, maxDurationSeconds)
        MediaTable.update({ MediaTable.id eq id }) {
            it[status] = next
            if (next == "ready" && event.type == "video.asset.ready") it[playbackId] = event.playbackId
            if (next == "errored") it[playbackId] = null
        }
        if (next == "errored") MuxUploadsTable.update({ MuxUploadsTable.mediaId eq id }) {
            it[errorCode] = if (event.duration != null && event.duration > maxDurationSeconds) "DURATION_LIMIT" else "PROCESSING_FAILED"
        }
    }

    private fun media(r: ResultRow) = Media(r[MediaTable.id], r[MediaTable.ownerUserId], "", r[MediaTable.contentType],
        "video", r[MediaTable.eventId], r[MediaTable.createdAtMs], "mux", r[MediaTable.status], r[MediaTable.playbackId])
}

internal fun muxNextStatus(current: String, event: MuxEvent, maxDuration: Int): String = when {
    current == "errored" -> current // deleted/failed assets must not be resurrected by an old ready delivery
    event.type == "video.asset.deleted" || event.type == "video.asset.errored" -> "errored"
    event.type == "video.asset.ready" -> if (event.playbackId.isNullOrBlank() || event.duration == null ||
        !event.duration.isFinite() || event.duration <= 0 || event.duration > maxDuration) "errored" else "ready"
    current == "ready" -> current
    event.type in setOf("video.upload.errored", "video.upload.cancelled", "video.upload.timed_out") -> "errored"
    event.type == "video.upload.asset_created" -> "processing"
    else -> current
}
