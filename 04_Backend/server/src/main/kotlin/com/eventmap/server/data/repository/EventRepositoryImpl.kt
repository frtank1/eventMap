package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.repository.EventRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class EventRepositoryImpl : EventRepository {

    override suspend fun all(): List<Event> = tx {
        EventsTable.selectAll().map(::toEvent)
    }

    override suspend fun byId(id: String): Event? = tx {
        EventsTable.selectAll().where { EventsTable.id eq id }.map(::toEvent).singleOrNull()
    }

    override suspend fun byOrganizer(organizerId: String): List<Event> = tx {
        EventsTable.selectAll().where { EventsTable.organizerId eq organizerId }.map(::toEvent)
    }

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun toEvent(r: ResultRow): Event {
        val orgId = r[EventsTable.organizerId]
        val orgName = orgId?.let {
            OrganizersTable.selectAll().where { OrganizersTable.id eq it }
                .map { row -> row[OrganizersTable.name] }.singleOrNull()
        } ?: ""
        return Event(
            id = r[EventsTable.id],
            title = r[EventsTable.title],
            description = r[EventsTable.description],
            category = r[EventsTable.category],
            status = r[EventsTable.status],
            startAt = r[EventsTable.startAt],
            endAt = r[EventsTable.endAt],
            location = GeoPoint(r[EventsTable.lat], r[EventsTable.lng], r[EventsTable.locationName]),
            price = Money(r[EventsTable.priceType], r[EventsTable.priceAmount], r[EventsTable.currency]),
            goingCount = r[EventsTable.goingCount],
            liveViewers = r[EventsTable.liveViewers],
            ratingAvg = r[EventsTable.ratingAvg],
            ratingCount = r[EventsTable.ratingCount],
            cover = r[EventsTable.cover],
            organizerId = orgId,
            organizerName = orgName,
        )
    }
}
