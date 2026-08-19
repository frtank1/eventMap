package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventQuery
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.OrganizerRepository

class GetEventsUseCase(private val repo: EventRepository) {
    suspend operator fun invoke(query: EventQuery = EventQuery()): List<Event> = repo.search(query)
}

class CreateEventUseCase(private val repo: EventRepository) {
    sealed interface Result {
        data class Ok(val event: Event) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(draft: Event): Result {
        if (draft.kind !in setOf("planned", "gathering", "live_signal", "private")) {
            return Result.Error("Неизвестный тип события")
        }
        if (draft.visibility !in setOf("public", "unlisted", "invite")) {
            return Result.Error("Неизвестный режим видимости")
        }
        if (draft.joinPolicy !in setOf("open", "approval", "invite")) {
            return Result.Error("Неизвестный режим вступления")
        }
        if (draft.audience !in setOf("everyone", "family", "adults")) {
            return Result.Error("Неизвестная аудитория")
        }
        if (draft.title.isBlank()) return Result.Error("Название обязательно")
        if (draft.category.isBlank()) return Result.Error("Категория обязательна")
        if (draft.startAt.isBlank()) return Result.Error("Укажите дату начала")
        if (draft.location.lat == 0.0 && draft.location.lng == 0.0) return Result.Error("Укажите локацию")
        if (draft.price.type == "paid" && draft.price.amount <= 0) return Result.Error("Цена платного события должна быть больше нуля")
        if (draft.capacity != null && draft.capacity !in 2..100_000) return Result.Error("Некорректный лимит участников")
        if (draft.kind == "live_signal" && draft.expiresAtMs == null) return Result.Error("Для живого сигнала нужен срок жизни")
        if (draft.kind == "live_signal" && draft.sourceType == "witness") {
            val creatorId = draft.organizerId.orEmpty()
            val existing = repo.findLiveSignalCandidate(draft)
            if (existing != null) {
                if (creatorId.isNotBlank()) repo.toggleSignal(existing.id, creatorId, "confirm")
                return Result.Ok(repo.byId(existing.id) ?: existing)
            }
            val created = repo.create(draft)
            if (creatorId.isNotBlank()) repo.toggleSignal(created.id, creatorId, "confirm")
            return Result.Ok(repo.byId(created.id) ?: created)
        }
        return Result.Ok(repo.create(draft))
    }
}

class UpdateEventUseCase(private val repo: EventRepository) {
    sealed interface Result {
        data class Ok(val event: Event) : Result
        data object NotFound : Result
        data object Forbidden : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(userId: String, id: String, draft: Event): Result {
        val existing = repo.byId(id) ?: return Result.NotFound
        if (existing.organizerId != userId) return Result.Forbidden
        if (draft.title.isBlank()) return Result.Error("Название обязательно")
        if (draft.category.isBlank()) return Result.Error("Категория обязательна")
        if (draft.startAt.isBlank()) return Result.Error("Укажите дату начала")
        if (draft.location.lat == 0.0 && draft.location.lng == 0.0) return Result.Error("Укажите локацию")
        if (draft.price.type == "paid" && draft.price.amount <= 0) return Result.Error("Цена платного события должна быть больше нуля")
        val saved = repo.update(id, draft) ?: return Result.NotFound
        return Result.Ok(saved)
    }
}

class DeleteEventUseCase(private val repo: EventRepository) {
    sealed interface Result {
        data object Ok : Result
        data object NotFound : Result
        data object Forbidden : Result
    }

    suspend operator fun invoke(userId: String, id: String): Result {
        val existing = repo.byId(id) ?: return Result.NotFound
        if (existing.organizerId != userId) return Result.Forbidden
        return if (repo.delete(id)) Result.Ok else Result.NotFound
    }
}

class GetEventUseCase(private val repo: EventRepository) {
    suspend operator fun invoke(id: String): Event? = repo.byId(id)
}

class GetOrganizerUseCase(private val repo: OrganizerRepository) {
    suspend operator fun invoke(id: String): Organizer? = repo.byId(id)
}

class GetOrganizerEventsUseCase(private val repo: EventRepository) {
    suspend operator fun invoke(organizerId: String): List<Event> = repo.byOrganizer(organizerId)
}
