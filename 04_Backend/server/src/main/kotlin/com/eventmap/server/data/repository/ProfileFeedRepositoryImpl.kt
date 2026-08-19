package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventRatingsTable
import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.NotificationsTable
import com.eventmap.server.data.db.OrganizerSubscriptionsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.data.db.SubscriptionRequestsTable
import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.domain.model.EventRating
import com.eventmap.server.domain.model.Notification
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.SubscriptionRequest
import com.eventmap.server.domain.repository.NotificationRepository
import com.eventmap.server.domain.repository.RatingRepository
import com.eventmap.server.domain.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

// ===== Подписки на организаторов (F1) · V9 =====

class SubscriptionRepositoryImpl : SubscriptionRepository {

    override suspend fun subscribe(userId: String, organizerId: String): Boolean = tx {
        val exists = OrganizerSubscriptionsTable.selectAll()
            .where { (OrganizerSubscriptionsTable.userId eq userId) and (OrganizerSubscriptionsTable.organizerId eq organizerId) }
            .any()
        if (exists) return@tx false
        OrganizerSubscriptionsTable.insert {
            it[OrganizerSubscriptionsTable.userId] = userId
            it[OrganizerSubscriptionsTable.organizerId] = organizerId
            it[createdAtMs] = System.currentTimeMillis()
        }
        syncSubscriberCount(organizerId)
        true
    }

    override suspend fun unsubscribe(userId: String, organizerId: String): Unit = tx {
        OrganizerSubscriptionsTable.deleteWhere {
            (OrganizerSubscriptionsTable.userId eq userId) and (OrganizerSubscriptionsTable.organizerId eq organizerId)
        }
        syncSubscriberCount(organizerId)
    }

    override suspend fun isSubscribed(userId: String, organizerId: String): Boolean = tx {
        OrganizerSubscriptionsTable.selectAll()
            .where { (OrganizerSubscriptionsTable.userId eq userId) and (OrganizerSubscriptionsTable.organizerId eq organizerId) }
            .any()
    }

    override suspend fun organizersOf(userId: String): List<Organizer> = tx {
        OrganizerSubscriptionsTable
            .join(OrganizersTable, JoinType.INNER, onColumn = OrganizerSubscriptionsTable.organizerId, otherColumn = OrganizersTable.id)
            .selectAll()
            .where { OrganizerSubscriptionsTable.userId eq userId }
            .map {
                Organizer(
                    id = it[OrganizersTable.id],
                    name = it[OrganizersTable.name],
                    bio = it[OrganizersTable.bio],
                    avatarUrl = it[OrganizersTable.avatarUrl],
                    isVerified = it[OrganizersTable.isVerified],
                    eventsCount = 0,
                    subscribers = it[OrganizersTable.subscribers],
                )
            }
    }

    override suspend fun countFor(userId: String): Int = tx {
        OrganizerSubscriptionsTable.selectAll().where { OrganizerSubscriptionsTable.userId eq userId }.count().toInt()
    }

    override suspend fun subscriberCount(organizerId: String): Int = tx {
        OrganizerSubscriptionsTable.selectAll().where { OrganizerSubscriptionsTable.organizerId eq organizerId }.count().toInt()
    }

    override suspend fun organizerIdsOf(userId: String): List<String> = tx {
        OrganizerSubscriptionsTable.selectAll()
            .where { OrganizerSubscriptionsTable.userId eq userId }
            .map { it[OrganizerSubscriptionsTable.organizerId] }
    }

    override suspend fun requestsFor(userId: String): List<SubscriptionRequest> = tx {
        SubscriptionRequestsTable
            .join(UsersTable, JoinType.LEFT, onColumn = SubscriptionRequestsTable.fromUserId, otherColumn = UsersTable.id)
            .selectAll()
            .where { (SubscriptionRequestsTable.targetUserId eq userId) and (SubscriptionRequestsTable.status eq "pending") }
            .orderBy(SubscriptionRequestsTable.createdAtMs, SortOrder.DESC)
            .map {
                SubscriptionRequest(
                    id = it[SubscriptionRequestsTable.id],
                    fromUserId = it[SubscriptionRequestsTable.fromUserId],
                    fromName = it.getOrNull(UsersTable.name) ?: "",
                    createdAtMs = it[SubscriptionRequestsTable.createdAtMs],
                )
            }
    }

    override suspend fun respondRequest(id: String, targetUserId: String, approve: Boolean): Boolean = tx {
        SubscriptionRequestsTable.update({
            (SubscriptionRequestsTable.id eq id) and
                (SubscriptionRequestsTable.targetUserId eq targetUserId) and
                (SubscriptionRequestsTable.status eq "pending")
        }) { it[status] = if (approve) "approved" else "rejected" } > 0
    }

    // денормализованный счётчик в organizers.subscribers (используется в OrganizerDto)
    private fun syncSubscriberCount(organizerId: String) {
        val count = OrganizerSubscriptionsTable.selectAll()
            .where { OrganizerSubscriptionsTable.organizerId eq organizerId }
            .count().toInt()
        OrganizersTable.update({ OrganizersTable.id eq organizerId }) { it[subscribers] = count }
    }
}

// ===== Уведомления (F4) · V9 =====

class NotificationRepositoryImpl : NotificationRepository {

    override suspend fun add(userId: String, type: String, text: String): Unit = tx {
        NotificationsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.type] = type
            it[NotificationsTable.text] = text
            it[createdAtMs] = System.currentTimeMillis()
        }
    }

    override suspend fun listFor(userId: String, limit: Int): List<Notification> = tx {
        NotificationsTable.selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAtMs, SortOrder.DESC)
            .limit(limit)
            .map {
                Notification(
                    id = it[NotificationsTable.id],
                    userId = it[NotificationsTable.userId],
                    type = it[NotificationsTable.type],
                    text = it[NotificationsTable.text],
                    createdAtMs = it[NotificationsTable.createdAtMs],
                    read = it[NotificationsTable.read],
                )
            }
    }

    override suspend fun markAllRead(userId: String): Unit = tx {
        NotificationsTable.update({ NotificationsTable.userId eq userId }) { it[read] = true }
    }
}

// ===== Оценки событий (P4) · V9 =====

class RatingRepositoryImpl : RatingRepository {

    override suspend fun rate(eventId: String, userId: String, value: Int, review: String, tags: List<String>): EventRating? = tx {
        EventsTable.selectAll().where { EventsTable.id eq eventId }.singleOrNull() ?: return@tx null
        val now = System.currentTimeMillis()
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        val tagsCsv = cleanTags.joinToString(",")
        val updated = EventRatingsTable.update({
            (EventRatingsTable.eventId eq eventId) and (EventRatingsTable.userId eq userId)
        }) {
            it[EventRatingsTable.value] = value
            it[EventRatingsTable.review] = review
            it[EventRatingsTable.tags] = tagsCsv
            it[createdAtMs] = now
        }
        val rid = if (updated == 0) {
            val newId = UUID.randomUUID().toString()
            EventRatingsTable.insert {
                it[id] = newId
                it[EventRatingsTable.eventId] = eventId
                it[EventRatingsTable.userId] = userId
                it[EventRatingsTable.value] = value
                it[EventRatingsTable.review] = review
                it[EventRatingsTable.tags] = tagsCsv
                it[createdAtMs] = now
            }
            newId
        } else {
            EventRatingsTable.selectAll()
                .where { (EventRatingsTable.eventId eq eventId) and (EventRatingsTable.userId eq userId) }
                .single()[EventRatingsTable.id]
        }

        // пересчёт кэша events.rating_avg / rating_count
        val avg = EventRatingsTable.select(EventRatingsTable.value.avg())
            .where { EventRatingsTable.eventId eq eventId }
            .single()[EventRatingsTable.value.avg()]?.toDouble() ?: 0.0
        val count = EventRatingsTable.selectAll().where { EventRatingsTable.eventId eq eventId }.count().toInt()
        EventsTable.update({ EventsTable.id eq eventId }) {
            it[ratingAvg] = avg
            it[ratingCount] = count
        }

        val authorName = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()?.get(UsersTable.name) ?: ""
        EventRating(rid, eventId, userId, authorName, value, review, cleanTags, now)
    }

    override suspend fun byEvent(eventId: String, limit: Int): List<EventRating> = tx {
        EventRatingsTable
            .join(UsersTable, JoinType.LEFT, onColumn = EventRatingsTable.userId, otherColumn = UsersTable.id)
            .selectAll()
            .where { EventRatingsTable.eventId eq eventId }
            .orderBy(EventRatingsTable.createdAtMs, SortOrder.DESC)
            .limit(limit)
            .map {
                EventRating(
                    id = it[EventRatingsTable.id],
                    eventId = it[EventRatingsTable.eventId],
                    userId = it[EventRatingsTable.userId],
                    authorName = it.getOrNull(UsersTable.name) ?: "",
                    value = it[EventRatingsTable.value],
                    review = it[EventRatingsTable.review],
                    tags = it[EventRatingsTable.tags].split(",").map { t -> t.trim() }.filter { t -> t.isNotEmpty() },
                    createdAtMs = it[EventRatingsTable.createdAtMs],
                )
            }
    }
}
