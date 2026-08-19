package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Ticket
import com.eventmap.server.domain.model.TicketGuest
import com.eventmap.server.domain.model.TicketOrder
import com.eventmap.server.domain.repository.AttendanceRepository
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.NotificationRepository
import com.eventmap.server.domain.repository.ReminderRepository
import com.eventmap.server.domain.repository.TicketRepository

// ===== Покупка билетов с гостями (P1, API §8.2) =====
//
// Правила:
//  - только платные события (free → 422, там «Приду»);
//  - qty = 1 (покупатель) + guests (1..9): с userId — попадут в список «идут», без — анонимный «+N»;
//  - оплата прошла → покупатель и гости-с-аккаунтом автоматически going + колокольчик (source=ticket);
//  - анонимные учитываются только в счётчике (AttendanceRepository.countGoing).

class PurchaseTicketsUseCase(
    private val events: EventRepository,
    private val tickets: TicketRepository,
    private val attendance: AttendanceRepository,
    private val reminders: ReminderRepository,
    private val inbox: NotificationRepository,
) {
    sealed interface Result {
        data class Ok(val order: TicketOrder, val goingCount: Long) : Result
        data object NotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        eventId: String,
        buyerId: String,
        guests: List<TicketGuest>,
        provider: String,
    ): Result {
        val event = events.byId(eventId) ?: return Result.NotFound
        if (event.price.type != "paid") {
            return Result.Rejected("FREE_EVENT", "Событие бесплатное — используй «Приду»")
        }
        if (event.status == "ended") {
            return Result.Rejected("ENDED", "Событие уже закончилось")
        }
        if (guests.size > 9) {
            return Result.Rejected("TOO_MANY", "Максимум 10 билетов за заказ")
        }
        val qty = guests.size + 1
        val order = tickets.createPaidOrder(
            eventId = eventId,
            buyerId = buyerId,
            guests = guests,
            amount = event.price.amount * qty,
            currency = event.price.currency,
            provider = provider,
        )
        // авто-«иду» + авто-колокольчик для всех с аккаунтом
        for (uid in (listOf(buyerId) + guests.mapNotNull { it.userId }).distinct()) {
            attendance.set(uid, eventId, "going")
            if (!reminders.isSet(uid, eventId)) reminders.toggle(uid, eventId, source = "ticket")
        }
        // уведомления F4 (in-app): покупателю и гостям с аккаунтом
        runCatching {
            inbox.add(buyerId, "event", "Билеты на «${event.title}» у тебя 🎟 Напоминание включено")
            guests.mapNotNull { it.userId }.distinct().filter { it != buyerId }.forEach {
                inbox.add(it, "event", "Тебе купили билет на «${event.title}» 🎟")
            }
        }
        return Result.Ok(order, attendance.countGoing(eventId))
    }
}

class GetMyTicketsUseCase(private val tickets: TicketRepository) {
    suspend operator fun invoke(userId: String): List<Ticket> = tickets.myTickets(userId)
}

class GetTicketQrUseCase(private val tickets: TicketRepository) {
    sealed interface Result {
        data class Ok(val qrSecret: String) : Result
        data object NotFound : Result
        data object Forbidden : Result
    }

    suspend operator fun invoke(ticketId: String, userId: String): Result {
        val t = tickets.byId(ticketId) ?: return Result.NotFound
        if (t.ownerUserId != userId) return Result.Forbidden
        return Result.Ok(t.qrSecret)
    }
}

class CheckinTicketUseCase(
    private val tickets: TicketRepository,
    private val events: EventRepository,
) {
    sealed interface Result {
        data class Ok(val ticket: Ticket) : Result
        data object NotFound : Result
        data object Forbidden : Result
    }

    /** Сканирует организатор события */
    suspend operator fun invoke(ticketId: String, qrSecret: String, scannerUserId: String): Result {
        val t = tickets.byId(ticketId) ?: return Result.NotFound
        val event = events.byId(t.eventId) ?: return Result.NotFound
        if (event.organizerId != scannerUserId) return Result.Forbidden
        val used = tickets.checkin(ticketId, qrSecret) ?: return Result.NotFound
        return Result.Ok(used)
    }
}
