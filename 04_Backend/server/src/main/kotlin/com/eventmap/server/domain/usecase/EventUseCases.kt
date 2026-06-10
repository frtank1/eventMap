package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.OrganizerRepository

class GetEventsUseCase(private val repo: EventRepository) {
    suspend operator fun invoke(): List<Event> = repo.all()
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
