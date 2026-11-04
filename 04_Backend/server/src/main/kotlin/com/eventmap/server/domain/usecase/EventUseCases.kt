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
        draft.validationError()?.let { return Result.Error(it) }
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
        draft.validationError()?.let { return Result.Error(it) }
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
