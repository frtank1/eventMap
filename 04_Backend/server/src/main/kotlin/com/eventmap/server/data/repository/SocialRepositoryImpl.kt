package com.eventmap.server.data.repository

import com.eventmap.server.data.db.AttendanceTable
import com.eventmap.server.data.db.CommentLikesTable
import com.eventmap.server.data.db.EventCommentsTable
import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.RemindersTable
import com.eventmap.server.data.db.TicketsTable
import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.domain.model.Attendance
import com.eventmap.server.domain.model.EventComment
import com.eventmap.server.domain.repository.AttendanceRepository
import com.eventmap.server.domain.repository.CommentRepository
import com.eventmap.server.domain.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

class CommentRepositoryImpl : CommentRepository {

    // комментарии + имя автора одним запросом
    private val joined = EventCommentsTable.join(
        UsersTable, JoinType.LEFT,
        onColumn = EventCommentsTable.authorId, otherColumn = UsersTable.id,
    )

    override suspend fun byEvent(eventId: String, viewerId: String?): List<EventComment> = tx {
        val likedIds: Set<String> = viewerId?.let { uid ->
            CommentLikesTable.selectAll()
                .where { CommentLikesTable.userId eq uid }
                .map { it[CommentLikesTable.commentId] }
                .toSet()
        } ?: emptySet()
        joined.selectAll()
            .where { EventCommentsTable.eventId eq eventId }
            .orderBy(EventCommentsTable.createdAtMs, SortOrder.DESC)
            .map { it.toComment(likedIds) }
    }

    override suspend fun add(eventId: String, authorId: String, body: String, parentId: String?): EventComment = tx {
        val cid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        EventCommentsTable.insert {
            it[id] = cid
            it[EventCommentsTable.eventId] = eventId
            it[EventCommentsTable.authorId] = authorId
            it[EventCommentsTable.parentId] = parentId
            it[EventCommentsTable.body] = body
            it[createdAtMs] = now
        }
        val authorName = UsersTable.selectAll()
            .where { UsersTable.id eq authorId }
            .singleOrNull()?.get(UsersTable.name) ?: ""
        EventComment(
            id = cid, eventId = eventId, authorId = authorId, authorName = authorName,
            parentId = parentId, body = body, likeCount = 0, likedByMe = false, createdAtMs = now,
        )
    }

    override suspend fun toggleLike(commentId: String, userId: String): Int? = tx {
        val exists = EventCommentsTable.selectAll()
            .where { EventCommentsTable.id eq commentId }
            .singleOrNull() ?: return@tx null
        val liked = CommentLikesTable.selectAll()
            .where { (CommentLikesTable.commentId eq commentId) and (CommentLikesTable.userId eq userId) }
            .any()
        val delta: Int
        if (liked) {
            CommentLikesTable.deleteWhere { (CommentLikesTable.commentId eq commentId) and (CommentLikesTable.userId eq userId) }
            delta = -1
        } else {
            CommentLikesTable.insert {
                it[CommentLikesTable.commentId] = commentId
                it[CommentLikesTable.userId] = userId
            }
            delta = 1
        }
        EventCommentsTable.update({ EventCommentsTable.id eq commentId }) {
            it[likeCount] = likeCount + delta
        }
        (exists[EventCommentsTable.likeCount] + delta).coerceAtLeast(0)
    }

    private fun ResultRow.toComment(likedIds: Set<String>): EventComment = EventComment(
        id = this[EventCommentsTable.id],
        eventId = this[EventCommentsTable.eventId],
        authorId = this[EventCommentsTable.authorId],
        authorName = this.getOrNull(UsersTable.name) ?: "",
        parentId = this[EventCommentsTable.parentId],
        body = this[EventCommentsTable.body],
        likeCount = this[EventCommentsTable.likeCount],
        likedByMe = this[EventCommentsTable.id] in likedIds,
        createdAtMs = this[EventCommentsTable.createdAtMs],
    )
}

class ReminderRepositoryImpl : ReminderRepository {

    override suspend fun toggle(userId: String, eventId: String, source: String): Boolean = tx {
        val existing = RemindersTable.selectAll()
            .where { (RemindersTable.userId eq userId) and (RemindersTable.eventId eq eventId) }
            .any()
        if (existing) {
            RemindersTable.deleteWhere { (RemindersTable.userId eq userId) and (RemindersTable.eventId eq eventId) }
            false
        } else {
            RemindersTable.insert {
                it[RemindersTable.userId] = userId
                it[RemindersTable.eventId] = eventId
                it[RemindersTable.sourceType] = source
            }
            true
        }
    }

    override suspend fun isSet(userId: String, eventId: String): Boolean = tx {
        RemindersTable.selectAll()
            .where { (RemindersTable.userId eq userId) and (RemindersTable.eventId eq eventId) }
            .any()
    }
}

class AttendanceRepositoryImpl : AttendanceRepository {

    override suspend fun set(userId: String, eventId: String, type: String): Unit = tx {
        val updated = AttendanceTable.update({
            (AttendanceTable.userId eq userId) and (AttendanceTable.eventId eq eventId)
        }) { it[AttendanceTable.type] = type }
        if (updated == 0) {
            AttendanceTable.insert {
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.eventId] = eventId
                it[AttendanceTable.type] = type
            }
        }
    }

    override suspend fun remove(userId: String, eventId: String): Unit = tx {
        AttendanceTable.deleteWhere { (AttendanceTable.userId eq userId) and (AttendanceTable.eventId eq eventId) }
    }

    override suspend fun get(userId: String, eventId: String): Attendance? = tx {
        AttendanceTable.selectAll()
            .where { (AttendanceTable.userId eq userId) and (AttendanceTable.eventId eq eventId) }
            .singleOrNull()
            ?.let { Attendance(it[AttendanceTable.userId], it[AttendanceTable.eventId], it[AttendanceTable.type]) }
    }

    // История H1: id событий юзера с отметкой type (was | going)
    override suspend fun eventIdsOf(userId: String, type: String): List<String> = tx {
        AttendanceTable.selectAll()
            .where { (AttendanceTable.userId eq userId) and (AttendanceTable.type eq type) }
            .map { it[AttendanceTable.eventId] }
    }

    // Участники D1c: id юзеров с отметкой type
    override suspend fun userIdsOf(eventId: String, type: String): List<String> = tx {
        AttendanceTable.selectAll()
            .where { (AttendanceTable.eventId eq eventId) and (AttendanceTable.type eq type) }
            .map { it[AttendanceTable.userId] }
    }

    // правило §8.2: счётчик = attendance(going) + анонимные активные билеты;
    // кэшируем в events.going_count, чтобы списки карты не считали на лету
    override suspend fun countGoing(eventId: String): Long = tx {
        val going = AttendanceTable.selectAll()
            .where { (AttendanceTable.eventId eq eventId) and (AttendanceTable.type eq "going") }
            .count()
        val anon = TicketsTable.selectAll()
            .where {
                (TicketsTable.eventId eq eventId) and
                    TicketsTable.ownerUserId.isNull() and
                    (TicketsTable.status eq "active")
            }
            .count()
        val total = going + anon
        EventsTable.update({ EventsTable.id eq eventId }) { it[goingCount] = total.toInt() }
        total
    }
}
