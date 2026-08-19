package com.eventmap.map.screen

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.EventRepository
import com.eventmap.event.EventSignalResult
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventComment
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket
import com.eventmap.event.models.UploadedMedia

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
    override suspend fun toggleSignal(id: String, type: String): KtorResponse<EventSignalResult> = stub()
    override suspend fun joinEvent(id: String, message: String): KtorResponse<String> = stub()
    override suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail> = stub()
    override suspend fun myEvents(): KtorResponse<List<EventDetail>> = stub()
    override suspend fun updateEvent(id: String, draft: CreateEventDraft): KtorResponse<EventDetail> = stub()
    override suspend fun deleteEvent(id: String): KtorResponse<Unit> = stub()
    override suspend fun friendsGoing(id: String): KtorResponse<com.eventmap.event.models.FriendsGoing> = stub()
    override suspend fun uploadMedia(
        bytes: ByteArray,
        contentType: String,
        kind: String,
        eventId: String?,
        onProgress: ((Float) -> Unit)?,
    ): KtorResponse<UploadedMedia> = stub()
    override suspend fun registerReel(
        eventId: String,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
    ): KtorResponse<Unit> = stub()
    override suspend fun purchase(id: String): KtorResponse<Ticket> = stub()
    override suspend fun getRatings(id: String): KtorResponse<List<Rating>> = stub()
    override suspend fun rateEvent(id: String, value: Int, review: String, tags: List<String>): KtorResponse<Unit> = stub()
    override suspend fun getOrganizer(id: String): KtorResponse<Organizer> = stub()
    override suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>> = stub()
    override suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit> = stub()
    override suspend fun getComments(eventId: String): KtorResponse<List<EventComment>> = stub()
    override suspend fun addComment(eventId: String, body: String, parentId: String?): KtorResponse<EventComment> = stub()
    override suspend fun toggleCommentLike(commentId: String): KtorResponse<Int> = stub()
}
