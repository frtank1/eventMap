package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.VenueCatalog
import java.time.Instant

/** Pilot city. A suggestion outside it is almost always a mistake or spam. */
object PilotCity {
    const val SOUTH = 50.9
    const val NORTH = 51.4
    const val WEST = 71.1
    const val EAST = 71.9
    fun contains(lat: Double, lng: Double) = lat in SOUTH..NORTH && lng in WEST..EAST
}

/**
 * «Предложить событие» without an account. Whatever the form sends, the result is a public
 * point event from a guest that waits in the moderation queue; nothing reaches the map until
 * a moderator publishes it.
 */
class SuggestEventUseCase(
    private val repo: EventRepository,
    private val venues: VenueCatalog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data object Accepted : Result
        data class Error(val message: String) : Result
        data object QueueFull : Result
    }

    suspend operator fun invoke(input: Event): Result {
        val now = clock()
        val venue = input.venue?.takeIf { venues.validate(it) == null }
        val draft = input.copy(
            id = "", status = "soon", kind = "planned", visibility = "public", joinPolicy = "open",
            sourceType = "guest", organizerId = null, organizerName = "", moderationStatus = "pending",
            presentation = "S", sceneTemplate = null, sceneParams = null, area = null, recurrence = null,
            cover = null, capacity = null, expiresAtMs = null, proofMediaId = null, externalId = null,
            reviewedBy = null, reviewedAtMs = null, rejectReason = null,
            // Suggestions keep at most a building; floors and rooms are confirmed by the venue later.
            venue = venue?.copy(floorId = null, roomId = null),
            title = input.title.trim(), description = input.description.trim(),
            contact = input.contact?.trim()?.takeIf { it.isNotEmpty() },
            sourceUrl = input.sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
        )
        draft.validationError(now)?.let { return Result.Error(it) }
        if (!PilotCity.contains(draft.location.lat, draft.location.lng)) return Result.Error("Пока принимаем события только в Астане")
        val start = Instant.parse(draft.startAt).toEpochMilli()
        val end = draft.endAt.takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilli() } ?: (start + 3 * 3_600_000L)
        if (end <= now) return Result.Error("Это событие уже прошло")
        if (start > now + 366L * 86_400_000L) return Result.Error("Событие должно быть в ближайший год")
        if (end - start > 31L * 86_400_000L) return Result.Error("Событие длиннее месяца лучше разбить на даты")
        val pending = repo.byModeration("pending", QUEUE_LIMIT + 1)
        // Double taps and re-sent forms do not fill the queue with copies.
        if (pending.any { it.startAt == draft.startAt && it.title.equals(draft.title, ignoreCase = true) }) return Result.Accepted
        if (pending.size >= QUEUE_LIMIT) return Result.QueueFull
        repo.create(draft)
        return Result.Accepted
    }

    companion object { const val QUEUE_LIMIT = 500 }
}

class ModerateEventUseCase(private val repo: EventRepository) {
    sealed interface Result {
        data class Ok(val event: Event) : Result
        data object NotPending : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(id: String, publish: Boolean, reason: String?, reviewer: String = "admin"): Result {
        val note = reason?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > 1000) return Result.Error("Причина: до 1000 символов")
        val existing = repo.byId(id) ?: return Result.NotPending
        if (existing.moderationStatus != "pending") return Result.NotPending
        // Re-checked at publication time: a suggestion can go stale while it waits.
        if (publish) existing.validationError()?.let { return Result.Error(it) }
        val saved = repo.moderate(id, publish, note, reviewer) ?: return Result.NotPending
        return Result.Ok(saved)
    }
}
