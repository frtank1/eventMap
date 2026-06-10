package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.repository.OrganizerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class OrganizerRepositoryImpl : OrganizerRepository {
    override suspend fun byId(id: String): Organizer? = newSuspendedTransaction(Dispatchers.IO) {
        val row = OrganizersTable.selectAll().where { OrganizersTable.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        val count = EventsTable.selectAll().where { EventsTable.organizerId eq id }.count().toInt()
        Organizer(
            id = row[OrganizersTable.id],
            name = row[OrganizersTable.name],
            bio = row[OrganizersTable.bio],
            avatarUrl = row[OrganizersTable.avatarUrl],
            isVerified = row[OrganizersTable.isVerified],
            eventsCount = count,
            subscribers = row[OrganizersTable.subscribers],
        )
    }
}
