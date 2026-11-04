package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.data.db.VideoLikesTable
import com.eventmap.server.data.db.VideosTable
import com.eventmap.server.data.db.MediaTable
import com.eventmap.server.domain.model.Video
import com.eventmap.server.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class VideoRepositoryImpl : VideoRepository {

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    // videos LEFT JOIN events (название/категория) LEFT JOIN users (автор)
    private val joined = VideosTable
        .join(EventsTable, JoinType.LEFT, onColumn = VideosTable.eventId, otherColumn = EventsTable.id)
        .join(UsersTable, JoinType.LEFT, onColumn = VideosTable.authorUserId, otherColumn = UsersTable.id)
        .join(MediaTable, JoinType.LEFT, onColumn = VideosTable.mediaId, otherColumn = MediaTable.id)

    override suspend fun feedNow(viewerId: String?): List<Video> = tx {
        val likedIds: Set<String> = viewerId?.let { uid ->
            VideoLikesTable.selectAll()
                .where { VideoLikesTable.userId eq uid }
                .map { it[VideoLikesTable.videoId] }
                .toSet()
        } ?: emptySet()

        joined.selectAll()
            .where {
                ((VideosTable.eventId.isNull()) or ((EventsTable.visibility eq "public") and (EventsTable.kind neq "private"))) and
                    ((VideosTable.mediaId.isNull()) or (MediaTable.status eq "ready"))
            }
            .orderBy(VideosTable.createdAtMs, SortOrder.DESC)
            .limit(50)
            .map { toVideo(it, likedIds) }
    }

    override suspend fun create(
        id: String,
        authorUserId: String,
        eventId: String?,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
    ): Video = tx {
        val now = System.currentTimeMillis()
        VideosTable.insert {
            it[VideosTable.id] = id
            it[VideosTable.authorUserId] = authorUserId
            it[VideosTable.eventId] = eventId
            it[VideosTable.mediaUrl] = mediaUrl
            it[thumbUrl] = thumb
            it[VideosTable.caption] = caption
            it[VideosTable.category] = category
            it[createdAtMs] = now
        }
        Video(
            id = id,
            eventId = eventId ?: "",
            caption = caption,
            mediaUrl = mediaUrl,
            thumb = thumb ?: mediaUrl,
            category = category,
        )
    }

    override suspend fun toggleReaction(videoId: String, userId: String): Int? = tx {
        VideosTable.selectAll().where { VideosTable.id eq videoId }.singleOrNull() ?: return@tx null
        val liked = !VideoLikesTable.selectAll()
            .where { (VideoLikesTable.videoId eq videoId) and (VideoLikesTable.userId eq userId) }
            .empty()
        val delta = if (liked) {
            VideoLikesTable.deleteWhere { (VideoLikesTable.videoId eq videoId) and (VideoLikesTable.userId eq userId) }
            -1
        } else {
            VideoLikesTable.insert {
                it[VideoLikesTable.videoId] = videoId
                it[VideoLikesTable.userId] = userId
            }
            1
        }
        VideosTable.update({ VideosTable.id eq videoId }) { it[likeCount] = likeCount + delta }
        VideosTable.selectAll().where { VideosTable.id eq videoId }.single()[VideosTable.likeCount]
    }

    private fun toVideo(r: ResultRow, likedIds: Set<String>): Video = Video(
        id = r[VideosTable.id],
        eventId = r[VideosTable.eventId] ?: "",
        eventTitle = r.getOrNull(EventsTable.title) ?: "",
        authorName = r.getOrNull(UsersTable.name) ?: "",
        caption = r[VideosTable.caption],
        mediaUrl = r[VideosTable.mediaUrl],
        thumb = r[VideosTable.thumbUrl] ?: r[VideosTable.mediaUrl],
        viewers = r[VideosTable.viewers],
        likes = r[VideosTable.likeCount],
        liked = r[VideosTable.id] in likedIds,
        category = r[VideosTable.category].ifBlank { r.getOrNull(EventsTable.category) ?: "" },
        status = r.getOrNull(MediaTable.status) ?: "ready",
        mediaId = r[VideosTable.mediaId],
    )
}
