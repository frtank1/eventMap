package com.eventmap.map.screen

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.EventRepository
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket

// Фейк для MapViewModel: важен только getEvents, остальное — заглушки.
class FakeEventRepository(
    private val eventsResponse: KtorResponse<List<EventDetail>>,
) : EventRepository {

    companion object {
        fun success(events: List<EventDetail>) = FakeEventRepository(KtorResponse.Success(events))
        fun failure(message: String = "network error") =
            FakeEventRepository(KtorResponse.Failure(ErrorModel(status = 500, customMessage = message)))
    }

    override suspend fun getEvents(): KtorResponse<List<EventDetail>> = eventsResponse

    private fun <T> stub(): KtorResponse<T> =
        KtorResponse.Failure(ErrorModel(customMessage = "not implemented in fake"))

    override suspend fun getEvent(id: String): KtorResponse<EventDetail> = stub()
    override suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>> = stub()
    override suspend fun getMedia(id: String): KtorResponse<List<MediaItem>> = stub()
    override suspend fun attend(id: String): KtorResponse<Unit> = stub()
    override suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail> = stub()
    override suspend fun purchase(id: String): KtorResponse<Ticket> = stub()
    override suspend fun getRatings(id: String): KtorResponse<List<Rating>> = stub()
    override suspend fun rateEvent(id: String, value: Int, review: String): KtorResponse<Unit> = stub()
    override suspend fun getOrganizer(id: String): KtorResponse<Organizer> = stub()
    override suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>> = stub()
    override suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit> = stub()
}
