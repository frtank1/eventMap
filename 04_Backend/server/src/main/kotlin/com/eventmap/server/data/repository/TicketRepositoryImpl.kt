package com.eventmap.server.data.repository

import com.eventmap.server.data.db.EventsTable
import com.eventmap.server.data.db.TicketOrdersTable
import com.eventmap.server.data.db.TicketsTable
import com.eventmap.server.domain.model.Ticket
import com.eventmap.server.domain.model.TicketGuest
import com.eventmap.server.domain.model.TicketOrder
import com.eventmap.server.domain.repository.TicketRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class TicketRepositoryImpl : TicketRepository {

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    // tickets LEFT JOIN events — заголовок события для «моих билетов»
    private val joined = TicketsTable.join(
        EventsTable, JoinType.LEFT,
        onColumn = TicketsTable.eventId, otherColumn = EventsTable.id,
    )

    // MVP: provider=mock → заказ сразу paid (реальный эквайринг — вебхук, §8.2 TODO)
    override suspend fun createPaidOrder(
        eventId: String,
        buyerId: String,
        guests: List<TicketGuest>,
        amount: Int,
        currency: String,
        provider: String,
    ): TicketOrder = tx {
        val orderId = UUID.randomUUID().toString()
        TicketOrdersTable.insert {
            it[id] = orderId
            it[TicketOrdersTable.eventId] = eventId
            it[TicketOrdersTable.buyerId] = buyerId
            it[qty] = guests.size + 1
            it[TicketOrdersTable.amount] = amount
            it[TicketOrdersTable.currency] = currency
            it[TicketOrdersTable.provider] = provider
            it[status] = "paid"
        }
        // билет покупателю + по билету каждому гостю (с аккаунтом или анонимно)
        val owners: List<String?> = listOf<String?>(buyerId) + guests.map { it.userId }
        val tickets = owners.map { owner ->
            val tid = UUID.randomUUID().toString()
            val qr = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
            TicketsTable.insert {
                it[id] = tid
                it[TicketsTable.orderId] = orderId
                it[TicketsTable.eventId] = eventId
                it[ownerUserId] = owner
                it[qrSecret] = qr
            }
            Ticket(id = tid, orderId = orderId, eventId = eventId, ownerUserId = owner, qrSecret = qr, status = "active")
        }
        TicketOrder(orderId, eventId, buyerId, owners.size, amount, currency, provider, "paid", tickets)
    }

    override suspend fun myTickets(userId: String): List<Ticket> = tx {
        joined.selectAll()
            .where { TicketsTable.ownerUserId eq userId }
            .map(::toTicket)
    }

    override suspend fun byId(ticketId: String): Ticket? = tx {
        joined.selectAll()
            .where { TicketsTable.id eq ticketId }
            .map(::toTicket)
            .singleOrNull()
    }

    override suspend fun checkin(ticketId: String, qrSecret: String): Ticket? = tx {
        val row = joined.selectAll()
            .where {
                (TicketsTable.id eq ticketId) and
                    (TicketsTable.qrSecret eq qrSecret) and
                    (TicketsTable.status eq "active")
            }
            .singleOrNull() ?: return@tx null
        TicketsTable.update({ TicketsTable.id eq ticketId }) { it[status] = "used" }
        toTicket(row).copy(status = "used")
    }

    private fun toTicket(r: ResultRow): Ticket = Ticket(
        id = r[TicketsTable.id],
        orderId = r[TicketsTable.orderId],
        eventId = r[TicketsTable.eventId],
        eventTitle = r.getOrNull(EventsTable.title) ?: "",
        ownerUserId = r[TicketsTable.ownerUserId],
        qrSecret = r[TicketsTable.qrSecret],
        status = r[TicketsTable.status],
    )
}
