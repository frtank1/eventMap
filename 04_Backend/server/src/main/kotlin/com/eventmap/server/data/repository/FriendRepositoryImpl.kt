package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.FriendshipsTable
import com.eventmap.server.data.db.InvitationGuestsTable
import com.eventmap.server.data.db.InvitationsTable
import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.domain.model.Friend
import com.eventmap.server.domain.model.Invitation
import com.eventmap.server.domain.model.InvitationGuest
import com.eventmap.server.domain.repository.FriendRepository
import com.eventmap.server.domain.repository.InvitationRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

private suspend fun <T> ftx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

// каноничная пара: a < b
private fun pair(x: String, y: String) = if (x < y) x to y else y to x

class FriendRepositoryImpl : FriendRepository {

    override suspend fun request(fromId: String, toId: String): Boolean = ftx {
        val (a, b) = pair(fromId, toId)
        val exists = FriendshipsTable.selectAll()
            .where { (FriendshipsTable.userA eq a) and (FriendshipsTable.userB eq b) }
            .any()
        if (exists) return@ftx false
        FriendshipsTable.insert {
            it[userA] = a
            it[userB] = b
            it[requestedBy] = fromId
        }
        true
    }

    override suspend fun respond(userId: String, otherId: String, accept: Boolean): Boolean = ftx {
        val (a, b) = pair(userId, otherId)
        val row = FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.userA eq a) and (FriendshipsTable.userB eq b) and
                    (FriendshipsTable.status eq "pending")
            }
            .singleOrNull() ?: return@ftx false
        // ответить может только адресат заявки
        if (row[FriendshipsTable.requestedBy] == userId) return@ftx false
        if (accept) {
            FriendshipsTable.update({ (FriendshipsTable.userA eq a) and (FriendshipsTable.userB eq b) }) {
                it[status] = "accepted"
            }
        } else {
            FriendshipsTable.deleteWhere { (FriendshipsTable.userA eq a) and (FriendshipsTable.userB eq b) }
        }
        true
    }

    override suspend fun listFor(userId: String): List<Friend> = ftx {
        val rows = FriendshipsTable.selectAll()
            .where { (FriendshipsTable.userA eq userId) or (FriendshipsTable.userB eq userId) }
            .toList()
        val otherIds = rows.map { if (it[FriendshipsTable.userA] == userId) it[FriendshipsTable.userB] else it[FriendshipsTable.userA] }
        val names = if (otherIds.isEmpty()) emptyMap()
        else UsersTable.selectAll().where { UsersTable.id inList otherIds }
            .associate { it[UsersTable.id] to it[UsersTable.name] }
        rows.map { r ->
            val other = if (r[FriendshipsTable.userA] == userId) r[FriendshipsTable.userB] else r[FriendshipsTable.userA]
            val status = when {
                r[FriendshipsTable.status] == "accepted" -> "accepted"
                r[FriendshipsTable.requestedBy] == userId -> "pending_out"
                else -> "pending_in"
            }
            Friend(other, names[other] ?: "", status)
        }
    }

    override suspend fun areFriends(a: String, b: String): Boolean = ftx {
        val (x, y) = pair(a, b)
        FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.userA eq x) and (FriendshipsTable.userB eq y) and
                    (FriendshipsTable.status eq "accepted")
            }
            .any()
    }
}

class InvitationRepositoryImpl : InvitationRepository {

    override suspend fun create(eventId: String, templateId: String, guestUserIds: List<String>, linkToken: String?): Invitation = ftx {
        val iid = UUID.randomUUID().toString()
        InvitationsTable.insert {
            it[id] = iid
            it[InvitationsTable.eventId] = eventId
            it[InvitationsTable.templateId] = templateId
            it[InvitationsTable.linkToken] = linkToken
        }
        guestUserIds.distinct().forEach { uid ->
            InvitationGuestsTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[invitationId] = iid
                it[userId] = uid
            }
        }
        load(iid)!!
    }

    override suspend fun byId(id: String): Invitation? = ftx { load(id) }

    override suspend fun byEvent(eventId: String): Invitation? = ftx {
        InvitationsTable.selectAll()
            .where { InvitationsTable.eventId eq eventId }
            .singleOrNull()?.let { load(it[InvitationsTable.id]) }
    }

    override suspend fun byLinkToken(token: String): Invitation? = ftx {
        InvitationsTable.selectAll()
            .where { InvitationsTable.linkToken eq token }
            .singleOrNull()?.let { load(it[InvitationsTable.id]) }
    }

    override suspend fun rsvpByUser(invitationId: String, userId: String, answer: String, plusOne: Int): Boolean = ftx {
        val updated = InvitationGuestsTable.update({
            (InvitationGuestsTable.invitationId eq invitationId) and (InvitationGuestsTable.userId eq userId)
        }) {
            it[rsvp] = answer
            it[InvitationGuestsTable.plusOne] = plusOne
            it[respondedAtMs] = System.currentTimeMillis()
        }
        updated > 0
    }

    override suspend fun rsvpByLink(invitationId: String, guestName: String, answer: String, plusOne: Int): Unit = ftx {
        InvitationGuestsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[InvitationGuestsTable.invitationId] = invitationId
            it[InvitationGuestsTable.guestName] = guestName
            it[rsvp] = answer
            it[InvitationGuestsTable.plusOne] = plusOne
            it[respondedAtMs] = System.currentTimeMillis()
        }
    }

    // внутри открытой транзакции
    private fun load(id: String): Invitation? {
        val inv = InvitationsTable.selectAll().where { InvitationsTable.id eq id }.singleOrNull() ?: return null
        val eventTitle = EventsTable.selectAll()
            .where { EventsTable.id eq inv[InvitationsTable.eventId] }
            .singleOrNull()?.get(EventsTable.title) ?: ""
        val guestRows = InvitationGuestsTable.selectAll()
            .where { InvitationGuestsTable.invitationId eq id }
            .toList()
        val userIds = guestRows.mapNotNull { it[InvitationGuestsTable.userId] }
        val names = if (userIds.isEmpty()) emptyMap()
        else UsersTable.selectAll().where { UsersTable.id inList userIds }
            .associate { it[UsersTable.id] to it[UsersTable.name] }
        val guests = guestRows.map { g ->
            val uid = g[InvitationGuestsTable.userId]
            InvitationGuest(
                id = g[InvitationGuestsTable.id],
                userId = uid,
                name = uid?.let { names[it] } ?: g[InvitationGuestsTable.guestName] ?: "Гость",
                rsvp = g[InvitationGuestsTable.rsvp],
                plusOne = g[InvitationGuestsTable.plusOne],
            )
        }
        return Invitation(
            id = inv[InvitationsTable.id],
            eventId = inv[InvitationsTable.eventId],
            eventTitle = eventTitle,
            templateId = inv[InvitationsTable.templateId],
            linkToken = inv[InvitationsTable.linkToken],
            guests = guests,
        )
    }
}
