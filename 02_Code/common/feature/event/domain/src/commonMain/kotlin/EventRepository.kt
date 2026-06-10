package com.eventmap.event

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket

interface EventRepository {
    suspend fun getEvents(): KtorResponse<List<EventDetail>>
    suspend fun getEvent(id: String): KtorResponse<EventDetail>
    suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>>
    suspend fun getMedia(id: String): KtorResponse<List<MediaItem>>
    suspend fun attend(id: String): KtorResponse<Unit>
    suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail>
    // P (ADR-002): билеты и рейтинги
    suspend fun purchase(id: String): KtorResponse<Ticket>
    suspend fun getRatings(id: String): KtorResponse<List<Rating>>
    suspend fun rateEvent(id: String, value: Int, review: String): KtorResponse<Unit>
    // Организаторы
    suspend fun getOrganizer(id: String): KtorResponse<Organizer>
    suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>>
    suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit>
}
