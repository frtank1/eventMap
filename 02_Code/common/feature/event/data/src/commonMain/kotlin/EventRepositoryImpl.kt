package com.eventmap.event.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.event.EventRepository
import com.eventmap.event.data.ktor.CreateEventRequest
import com.eventmap.event.data.ktor.EventRemoteDataSource
import com.eventmap.event.data.ktor.RatingRequest
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class EventRepositoryImpl(
    private val remote: EventRemoteDataSource,
) : EventRepository {

    override suspend fun getEvents(): KtorResponse<List<EventDetail>> =
        safeRequest { remote.getEvents() }

    override suspend fun getEvent(id: String): KtorResponse<EventDetail> =
        safeRequest { remote.getEvent(id) }

    override suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>> =
        safeRequest { remote.getParticipants(id, type) }

    override suspend fun getMedia(id: String): KtorResponse<List<MediaItem>> =
        safeRequest { remote.getMedia(id) }

    override suspend fun attend(id: String): KtorResponse<Unit> =
        unitRequest { remote.attend(id) }

    override suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail> =
        safeRequest {
            remote.createEvent(
                CreateEventRequest(
                    title = draft.title,
                    description = draft.description,
                    coverMediaId = draft.coverMediaId,
                    location = draft.location,
                    startAt = draft.startAt,
                    endAt = draft.endAt,
                    category = draft.category,
                    price = draft.price,
                    limit = draft.limit,
                )
            )
        }

    override suspend fun purchase(id: String): KtorResponse<Ticket> =
        safeRequest { remote.purchase(id) }

    override suspend fun getRatings(id: String): KtorResponse<List<Rating>> =
        safeRequest { remote.getRatings(id) }

    override suspend fun rateEvent(id: String, value: Int, review: String): KtorResponse<Unit> =
        unitRequest { remote.rateEvent(id, RatingRequest(value, review)) }

    override suspend fun getOrganizer(id: String): KtorResponse<Organizer> =
        safeRequest { remote.getOrganizer(id) }

    override suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>> =
        safeRequest { remote.getOrganizerEvents(id) }

    override suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit> =
        unitRequest { if (subscribe) remote.subscribe(organizerId) else remote.unsubscribe(organizerId) }

    private suspend inline fun unitRequest(call: () -> HttpResponse): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
