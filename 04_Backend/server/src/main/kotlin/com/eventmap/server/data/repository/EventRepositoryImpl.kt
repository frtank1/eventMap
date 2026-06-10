package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.OrganizersTable
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventQuery
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.repository.EventRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class EventRepositoryImpl : EventRepository {

    // events LEFT JOIN organizers — имя организатора одним запросом (без N+1)
    private val joined = EventsTable.join(
        OrganizersTable, JoinType.LEFT,
        onColumn = EventsTable.organizerId, otherColumn = OrganizersTable.id,
    )

    override suspend fun search(query: EventQuery): List<Event> = tx {
        val q = joined.selectAll()
        query.bbox?.let { b ->
            q.andWhere {
                (EventsTable.lat greaterEq b.swLat) and (EventsTable.lat lessEq b.neLat) and
                    (EventsTable.lng greaterEq b.swLng) and (EventsTable.lng lessEq b.neLng)
            }
        }
        if (query.categories.isNotEmpty()) q.andWhere { EventsTable.category inList query.categories }
        query.priceType?.let { p -> q.andWhere { EventsTable.priceType eq p } }
        q.map(::toEvent)
    }

    override suspend fun byId(id: String): Event? = tx {
        joined.selectAll().where { EventsTable.id eq id }.map(::toEvent).singleOrNull()
    }

    override suspend fun byOrganizer(organizerId: String): List<Event> = tx {
        joined.selectAll().where { EventsTable.organizerId eq organizerId }.map(::toEvent)
    }

    override suspend fun create(draft: Event): Event = tx {
        val eid = UUID.randomUUID().toString()
        EventsTable.insert {
            it[id] = eid
            it[title] = draft.title
            it[description] = draft.description
            it[category] = draft.category
            it[status] = draft.status
            it[startAt] = draft.startAt
            it[endAt] = draft.endAt
            it[lat] = draft.location.lat
            it[lng] = draft.location.lng
            it[locationName] = draft.location.name
            it[priceType] = draft.price.type
            it[priceAmount] = draft.price.amount
            it[currency] = draft.price.currency
            it[cover] = draft.cover
            it[organizerId] = draft.organizerId
            it[capacity] = draft.capacity
        }
        draft.copy(id = eid)
    }

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun toEvent(r: ResultRow): Event = Event(
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
        organizerId = r[EventsTable.organizerId],
        organizerName = r.getOrNull(OrganizersTable.name) ?: "",
        capacity = r[EventsTable.capacity],
    )
}
