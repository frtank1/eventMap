package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.EventComment
import com.eventmap.server.domain.repository.AttendanceRepository
import com.eventmap.server.domain.repository.CommentRepository
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.ReminderRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ===== Комментарии (D9) =====

class GetCommentsUseCase(private val comments: CommentRepository) {
    suspend operator fun invoke(eventId: String, viewerId: String?): List<EventComment> =
        comments.byEvent(eventId, viewerId)
}

class AddCommentUseCase(private val comments: CommentRepository) {
    sealed interface Result {
        data class Ok(val comment: EventComment) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(eventId: String, authorId: String, body: String, parentId: String?): Result {
        val text = body.trim()
        if (text.isEmpty()) return Result.Error("Пустой комментарий")
        if (text.length > 2000) return Result.Error("Слишком длинный комментарий (макс 2000)")
        return Result.Ok(comments.add(eventId, authorId, text, parentId))
    }
}

class ToggleCommentLikeUseCase(private val comments: CommentRepository) {
    /** @return новое число лайков; null — комментарий не найден */
    suspend operator fun invoke(commentId: String, userId: String): Int? = comments.toggleLike(commentId, userId)
}

// ===== Колокольчик (D11) =====

class ToggleReminderUseCase(private val reminders: ReminderRepository) {
    /** @return true = напоминание включено */
    suspend operator fun invoke(userId: String, eventId: String): Boolean = reminders.toggle(userId, eventId)
}

// ===== Присутствие (D1c): «Приду» / «Я тоже здесь» / отмена =====
//
// Правила (SCREENS.md раздел D, API §3/§8.2):
//  - going  — только ДО старта и только для бесплатных (платные → покупка билета);
//  - here   — только когда событие live И гео совпадает (<= geoRadiusM, по умолчанию 300 м);
//  - cancel — снять отметку.

class AttendUseCase(
    private val events: EventRepository,
    private val attendance: AttendanceRepository,
) {
    sealed interface Result {
        data class Ok(val status: String?, val goingCount: Long) : Result
        data object NotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        userId: String,
        eventId: String,
        action: String,        // going | here | cancel
        lat: Double? = null,
        lng: Double? = null,
    ): Result {
        val event = events.byId(eventId) ?: return Result.NotFound
        when (action) {
            "cancel" -> attendance.remove(userId, eventId)

            "going" -> {
                if (event.price.type == "paid") {
                    return Result.Rejected("PAID_ONLY", "Платное событие — отметка только через покупку билета")
                }
                if (event.status != "soon") {
                    return Result.Rejected("ALREADY_STARTED", "Событие уже идёт — используй «Я тоже здесь» на месте")
                }
                attendance.set(userId, eventId, "going")
            }

            "here" -> {
                if (event.status != "live") {
                    return Result.Rejected("NOT_LIVE", "«Я тоже здесь» доступно только когда событие идёт")
                }
                if (lat == null || lng == null) {
                    return Result.Rejected("GEO_REQUIRED", "Нужна геолокация для отметки присутствия")
                }
                val dist = haversineMeters(event.location.lat, event.location.lng, lat, lng)
                if (dist > event.geoRadiusM) {
                    return Result.Rejected("TOO_FAR", "Ты в ${dist.toInt()} м — кнопка активна в радиусе ${event.geoRadiusM} м")
                }
                attendance.set(userId, eventId, "going")
            }

            else -> return Result.Rejected("BAD_ACTION", "action: going | here | cancel")
        }
        val status = attendance.get(userId, eventId)?.type
        return Result.Ok(status, attendance.countGoing(eventId))
    }
}

internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
