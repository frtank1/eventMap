package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.repository.OrganizerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class OrganizerRepositoryImpl : OrganizerRepository {
    override suspend fun byId(id: String): Organizer? = newSuspendedTransaction(Dispatchers.IO) {
        val row = OrganizersTable.selectAll().where { OrganizersTable.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        val count = EventsTable.selectAll().where { EventsTable.organizerId eq id }.count().toInt()
        row.toOrganizer(eventsCount = count)
    }

    // Поиск G2 (места/организаторы)
    override suspend fun searchByName(q: String, limit: Int): List<Organizer> = newSuspendedTransaction(Dispatchers.IO) {
        OrganizersTable.selectAll()
            .where { OrganizersTable.name.lowerCase() like "%${q.lowercase()}%" }
            .limit(limit)
            .map { it.toOrganizer(eventsCount = 0) }
    }

    private fun ResultRow.toOrganizer(eventsCount: Int) = Organizer(
        id = this[OrganizersTable.id],
        name = this[OrganizersTable.name],
        bio = this[OrganizersTable.bio],
        avatarUrl = this[OrganizersTable.avatarUrl],
        isVerified = this[OrganizersTable.isVerified],
        eventsCount = eventsCount,
        subscribers = this[OrganizersTable.subscribers],
    )
}
