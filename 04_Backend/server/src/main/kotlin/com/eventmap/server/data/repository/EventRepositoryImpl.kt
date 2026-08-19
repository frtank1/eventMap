package com.eventmap.server.data.repository

import com.eventmap.server.data.db.AttendanceTable
import com.eventmap.server.data.db.EventCommentsTable
import com.eventmap.server.data.db.EventJoinRequestsTable
import com.eventmap.server.data.db.EventSignalsTable
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class EventRepositoryImpl : EventRepository {

    // events LEFT JOIN organizers — имя организатора одним запросом (без N+1)
    private val joined = EventsTable.join(
        OrganizersTable, JoinType.LEFT,
        onColumn = EventsTable.organizerId, otherColumn = OrganizersTable.id,
    )

    override suspend fun search(query: EventQuery): List<Event> = tx {
        // приватные события (раздел N) не попадают в публичную выдачу карты
        val q = joined.selectAll().where { EventsTable.visibility eq "public" }
        query.bbox?.let { b ->
            q.andWhere {
                (EventsTable.lat greaterEq b.swLat) and (EventsTable.lat lessEq b.neLat) and
                    (EventsTable.lng greaterEq b.swLng) and (EventsTable.lng lessEq b.neLng)
            }
        }
        if (query.categories.isNotEmpty()) q.andWhere { EventsTable.category inList query.categories }
        query.priceType?.let { p -> q.andWhere { EventsTable.priceType eq p } }
        q.map(::toEvent).withLiveCounts()
    }

    override suspend fun byId(id: String): Event? = tx {
        joined.selectAll().where { EventsTable.id eq id }.map(::toEvent).withLiveCounts().singleOrNull()
    }

    override suspend fun byOrganizer(organizerId: String): List<Event> = tx {
        joined.selectAll().where { EventsTable.organizerId eq organizerId }.map(::toEvent).withLiveCounts()
    }

    override suspend fun byIds(ids: List<String>): List<Event> = tx {
        if (ids.isEmpty()) emptyList()
        else joined.selectAll().where { EventsTable.id inList ids }.map(::toEvent).withLiveCounts()
    }

    // Лента F1→feed: ближайшие события организаторов, на которых подписан юзер
    override suspend fun byOrganizers(organizerIds: List<String>, limit: Int): List<Event> = tx {
        if (organizerIds.isEmpty()) emptyList()
        else joined.selectAll()
            .where { (EventsTable.organizerId inList organizerIds) and (EventsTable.visibility eq "public") }
            .orderBy(EventsTable.startAt, SortOrder.ASC)
            .limit(limit)
            .map(::toEvent)
            .withLiveCounts()
    }

    // Поиск G2 (события): регистронезависимо по названию/описанию/месту
    override suspend fun searchText(q: String, limit: Int): List<Event> = tx {
        val pattern = "%${q.lowercase()}%"
        joined.selectAll()
            .where {
                (EventsTable.visibility eq "public") and (
                    (EventsTable.title.lowerCase() like pattern) or
                        (EventsTable.description.lowerCase() like pattern) or
                        (EventsTable.locationName.lowerCase() like pattern)
                    )
            }
            .limit(limit)
            .map(::toEvent)
            .withLiveCounts()
    }

    // Поиск G2 (жанры): отличные категории, содержащие запрос
    override suspend fun categoriesLike(q: String, limit: Int): List<String> = tx {
        val pattern = "%${q.lowercase()}%"
        EventsTable.select(EventsTable.category)
            .where { (EventsTable.category.lowerCase() like pattern) and (EventsTable.category neq "") }
            .withDistinct()
            .limit(limit)
            .map { it[EventsTable.category] }
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
            it[isAdult] = draft.isAdult
            it[visibility] = draft.visibility
            it[kind] = draft.kind
            it[sourceType] = draft.sourceType
            it[joinPolicy] = draft.joinPolicy
            it[audience] = draft.audience
            it[minAge] = draft.minAge
            it[expiresAtMs] = draft.expiresAtMs
            it[proofMediaId] = draft.proofMediaId
        }
        draft.copy(id = eid)
    }

    override suspend fun update(id: String, draft: Event): Event? = tx {
        val updated = EventsTable.update({ EventsTable.id eq id }) {
            it[title] = draft.title
            it[description] = draft.description
            it[category] = draft.category
            it[startAt] = draft.startAt
            it[endAt] = draft.endAt
            it[lat] = draft.location.lat
            it[lng] = draft.location.lng
            it[locationName] = draft.location.name
            it[priceType] = draft.price.type
            it[priceAmount] = draft.price.amount
            it[currency] = draft.price.currency
            // обложку меняем только если прислали новую — null означает «оставить как есть»
            if (draft.cover != null) it[cover] = draft.cover
            it[capacity] = draft.capacity
            it[kind] = draft.kind
            it[sourceType] = draft.sourceType
            it[visibility] = draft.visibility
            it[joinPolicy] = draft.joinPolicy
            it[audience] = draft.audience
            it[minAge] = draft.minAge
            it[expiresAtMs] = draft.expiresAtMs
            it[proofMediaId] = draft.proofMediaId
        }
        if (updated == 0) null
        else EventsTable.selectAll().where { EventsTable.id eq id }.singleOrNull()?.let(::toEvent)
    }

    override suspend fun delete(id: String): Boolean = tx {
        EventsTable.deleteWhere { EventsTable.id eq id } > 0
    }

    override suspend fun toggleSignal(eventId: String, userId: String, type: String): Pair<Boolean, Int>? = tx {
        if (type !in setOf("confirm", "interest")) return@tx null
        if (!EventsTable.selectAll().where { EventsTable.id eq eventId }.any()) return@tx null
        val exists = EventSignalsTable.selectAll().where {
            (EventSignalsTable.eventId eq eventId) and
                (EventSignalsTable.userId eq userId) and
                (EventSignalsTable.signalType eq type)
        }.any()
        if (exists) {
            EventSignalsTable.deleteWhere {
                (EventSignalsTable.eventId eq eventId) and
                    (EventSignalsTable.userId eq userId) and
                    (EventSignalsTable.signalType eq type)
            }
        } else {
            EventSignalsTable.insertIgnore {
                it[EventSignalsTable.eventId] = eventId
                it[EventSignalsTable.userId] = userId
                it[signalType] = type
                it[createdAtMs] = System.currentTimeMillis()
            }
        }
        val total = EventSignalsTable.selectAll().where {
            (EventSignalsTable.eventId eq eventId) and (EventSignalsTable.signalType eq type)
        }.count().toInt()
        EventsTable.update({ EventsTable.id eq eventId }) {
            if (type == "confirm") it[confirmationCount] = total else it[interestCount] = total
        }
        (!exists) to total
    }

    override suspend fun requestJoin(eventId: String, userId: String, message: String): String? = tx {
        val event = EventsTable.selectAll().where { EventsTable.id eq eventId }.singleOrNull() ?: return@tx null
        val policy = event[EventsTable.joinPolicy]
        if (policy == "invite") return@tx "invite_required"
        val status = if (policy == "approval") "pending" else "approved"
        val existing = EventJoinRequestsTable.selectAll().where {
            (EventJoinRequestsTable.eventId eq eventId) and (EventJoinRequestsTable.userId eq userId)
        }.any()
        if (existing) {
            EventJoinRequestsTable.update({
                (EventJoinRequestsTable.eventId eq eventId) and (EventJoinRequestsTable.userId eq userId)
            }) {
                it[EventJoinRequestsTable.status] = status
                it[EventJoinRequestsTable.message] = message.take(500)
            }
        } else {
            EventJoinRequestsTable.insert {
                it[EventJoinRequestsTable.eventId] = eventId
                it[EventJoinRequestsTable.userId] = userId
                it[EventJoinRequestsTable.status] = status
                it[EventJoinRequestsTable.message] = message.take(500)
                it[createdAtMs] = System.currentTimeMillis()
            }
        }
        status
    }

    override suspend fun reviewJoin(
        eventId: String,
        ownerId: String,
        userId: String,
        approve: Boolean,
    ): String? = tx {
        val owned = EventsTable.selectAll().where {
            (EventsTable.id eq eventId) and (EventsTable.organizerId eq ownerId)
        }.any()
        if (!owned) return@tx null
        val status = if (approve) "approved" else "rejected"
        val changed = EventJoinRequestsTable.update({
            (EventJoinRequestsTable.eventId eq eventId) and (EventJoinRequestsTable.userId eq userId)
        }) { it[EventJoinRequestsTable.status] = status }
        status.takeIf { changed > 0 }
    }

    override suspend fun findLiveSignalCandidate(draft: Event, radiusDegrees: Double): Event? = tx {
        val now = System.currentTimeMillis()
        val candidates = joined.selectAll().where {
            (EventsTable.kind eq "live_signal") and
                (EventsTable.category eq draft.category) and
                (EventsTable.status neq "ended") and
                (EventsTable.lat greaterEq draft.location.lat - radiusDegrees) and
                (EventsTable.lat lessEq draft.location.lat + radiusDegrees) and
                (EventsTable.lng greaterEq draft.location.lng - radiusDegrees) and
                (EventsTable.lng lessEq draft.location.lng + radiusDegrees)
        }.map(::toEvent)
        val wanted = normalizeSignalTitle(draft.title)
        candidates
            .filter { it.expiresAtMs == null || it.expiresAtMs > now }
            .minByOrNull { titleDistance(wanted, normalizeSignalTitle(it.title)) }
            ?.takeIf { titleDistance(wanted, normalizeSignalTitle(it.title)) <= 2 }
    }

    // Живые счётчики поверх «затравки» сида: реальные отметки attendance прибавляются
    // к goingCount (и к liveViewers у идущих событий), комментарии считаются честно.
    // Один GROUP BY на пачку событий — без N+1. Вызывать ВНУТРИ tx.
    private fun List<Event>.withLiveCounts(): List<Event> {
        if (isEmpty()) return this
        val ids = map { it.id }
        val attendCount = AttendanceTable.eventId.count()
        val attends = AttendanceTable
            .select(AttendanceTable.eventId, attendCount)
            .where { AttendanceTable.eventId inList ids }
            .groupBy(AttendanceTable.eventId)
            .associate { it[AttendanceTable.eventId] to it[attendCount] }
        val commentCount = EventCommentsTable.eventId.count()
        val comments = EventCommentsTable
            .select(EventCommentsTable.eventId, commentCount)
            .where { EventCommentsTable.eventId inList ids }
            .groupBy(EventCommentsTable.eventId)
            .associate { it[EventCommentsTable.eventId] to it[commentCount] }
        return map { e ->
            val real = (attends[e.id] ?: 0L).toInt()
            e.copy(
                goingCount = e.goingCount + real,
                liveViewers = if (e.status == "live") e.liveViewers + real else e.liveViewers,
                commentsCount = (comments[e.id] ?: 0L).toInt(),
            )
        }
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
        isAdult = r[EventsTable.isAdult],
        visibility = r[EventsTable.visibility],
        geoRadiusM = r[EventsTable.geoRadiusM],
        kind = r[EventsTable.kind],
        sourceType = r[EventsTable.sourceType],
        joinPolicy = r[EventsTable.joinPolicy],
        audience = r[EventsTable.audience],
        minAge = r[EventsTable.minAge],
        expiresAtMs = r[EventsTable.expiresAtMs],
        proofMediaId = r[EventsTable.proofMediaId],
        confirmationCount = r[EventsTable.confirmationCount],
        interestCount = r[EventsTable.interestCount],
    )

    private fun normalizeSignalTitle(value: String): Set<String> =
        value.lowercase()
            .replace(Regex("[^а-яa-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()

    private fun titleDistance(a: Set<String>, b: Set<String>): Int =
        if (a.isEmpty() || b.isEmpty()) 99 else (a union b).size - (a intersect b).size
}
