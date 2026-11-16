package com.eventmap.server.data.repository

import com.eventmap.server.data.db.MediaTable
import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class MediaRepositoryImpl : MediaRepository {

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun create(
        id: String,
        ownerUserId: String,
        url: String,
        contentType: String,
        kind: String,
        eventId: String?,
    ): Media = tx {
        val now = System.currentTimeMillis()
        MediaTable.insert {
            it[MediaTable.id] = id
            it[MediaTable.ownerUserId] = ownerUserId
            it[MediaTable.url] = url
            it[MediaTable.contentType] = contentType
            it[MediaTable.kind] = kind
            it[MediaTable.eventId] = eventId
            it[createdAtMs] = now
        }
        Media(id, ownerUserId, url, contentType, kind, eventId, now)
    }

    override suspend fun byEvent(eventId: String): List<Media> = tx {
        val attached = MediaTable.selectAll()
            .where { MediaTable.eventId eq eventId }
            .orderBy(MediaTable.createdAtMs, SortOrder.DESC)
            .map(::toMedia)
        // The creation form uploads the cover BEFORE the event exists. Its event_id
        // is null; the authoritative association is events.cover, not upload order.
        val event = EventsTable.selectAll().where { EventsTable.id eq eventId }.singleOrNull()
        val coverId = event?.get(EventsTable.cover)
        val cover = coverId?.let { id ->
            MediaTable.selectAll().where { MediaTable.id eq id }.singleOrNull()?.let(::toMedia)
        }?.takeIf {
            it.kind == "image" && it.status == "ready" && it.ownerUserId == event?.get(EventsTable.organizerId) &&
                (it.eventId == null || it.eventId == eventId)
        }
        (listOfNotNull(cover) + attached).distinctBy { it.id }
    }

    override suspend fun byId(id: String): Media? = tx {
        MediaTable.selectAll()
            .where { MediaTable.id eq id }
            .map(::toMedia)
            .singleOrNull()
    }

    private fun toMedia(r: ResultRow): Media = Media(
        id = r[MediaTable.id],
        ownerUserId = r[MediaTable.ownerUserId],
        url = r[MediaTable.url],
        contentType = r[MediaTable.contentType],
        kind = r[MediaTable.kind],
        eventId = r[MediaTable.eventId],
        createdAt = r[MediaTable.createdAtMs],
        provider = r[MediaTable.provider],
        status = r[MediaTable.status],
        playbackId = r[MediaTable.playbackId],
    )
}
