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
        if (draft.title.isBlank()) return Result.Error("Название обязательно")
        if (draft.category.isBlank()) return Result.Error("Категория обязательна")
        if (draft.startAt.isBlank()) return Result.Error("Укажите дату начала")
        if (draft.location.lat == 0.0 && draft.location.lng == 0.0) return Result.Error("Укажите локацию")
        if (draft.price.type == "paid" && draft.price.amount <= 0) return Result.Error("Цена платного события должна быть больше нуля")
        return Result.Ok(repo.create(draft))
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
