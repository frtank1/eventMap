package com.eventmap.event.ui.viewmodel

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

// In-memory responses for publication and failure-path regression tests.
class FakeEventRepository(
    private val eventsResponse: KtorResponse<List<EventDetail>> = KtorResponse.Success(emptyList()),
) : EventRepository {

    var eventResponse: KtorResponse<EventDetail> = KtorResponse.Success(EventDetail("event-1", "Football", startAt = "2030-06-01T15:00:00Z"))
    var creates = 0
    var updates = 0
    var uploads = 0
    var registrations = 0
    var draft: CreateEventDraft? = null
    var failUploadKind: String? = null
    var failRegistration = false
    var uploadedMedia: UploadedMedia? = null
    var registeredMediaId: String? = null
    var createFailure = false
    var purchaseResponse: KtorResponse<Ticket> = stub()
    var attendResponse: KtorResponse<Unit> = stub()
    var ratingResponse: KtorResponse<Unit> = stub()

    companion object {
        fun success(events: List<EventDetail>) = FakeEventRepository(KtorResponse.Success(events))
        fun failure(message: String = "network error") =
            FakeEventRepository(KtorResponse.Failure(ErrorModel(status = 500, customMessage = message)))
    }

    override suspend fun getEvents(): KtorResponse<List<EventDetail>> = eventsResponse

    private fun <T> stub(): KtorResponse<T> =
        KtorResponse.Failure(ErrorModel(customMessage = "not implemented in fake"))

    override suspend fun getEvent(id: String): KtorResponse<EventDetail> = eventResponse
    override suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>> = stub()
    override suspend fun getMedia(id: String): KtorResponse<List<MediaItem>> = stub()
    override suspend fun attend(id: String): KtorResponse<Unit> = attendResponse
    override suspend fun toggleSignal(id: String, type: String): KtorResponse<EventSignalResult> = stub()
    override suspend fun joinEvent(id: String, message: String): KtorResponse<String> = stub()
    override suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail> {
        creates++
        this.draft = draft
        return if (createFailure) stub() else eventResponse
    }
    override suspend fun myEvents(): KtorResponse<List<EventDetail>> = stub()
    override suspend fun updateEvent(id: String, draft: CreateEventDraft): KtorResponse<EventDetail> {
        updates++
        this.draft = draft
        return eventResponse
    }
    override suspend fun deleteEvent(id: String): KtorResponse<Unit> = stub()
    override suspend fun friendsGoing(id: String): KtorResponse<com.eventmap.event.models.FriendsGoing> = stub()
    override suspend fun uploadMedia(
        bytes: ByteArray,
        contentType: String,
        kind: String,
        eventId: String?,
        onProgress: ((Float) -> Unit)?,
    ): KtorResponse<UploadedMedia> {
        uploads++
        return if (kind == failUploadKind) stub() else KtorResponse.Success(uploadedMedia ?: UploadedMedia("media-$kind", "https://example.test/$kind"))
    }
    override suspend fun registerReel(
        eventId: String,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
        mediaId: String?,
    ): KtorResponse<Unit> {
        registrations++
        registeredMediaId = mediaId
        return if (failRegistration) stub() else KtorResponse.Success(Unit)
    }
    override suspend fun purchase(id: String): KtorResponse<Ticket> = purchaseResponse
    override suspend fun getRatings(id: String): KtorResponse<List<Rating>> = stub()
    override suspend fun rateEvent(id: String, value: Int, review: String, tags: List<String>): KtorResponse<Unit> = ratingResponse
    override suspend fun getOrganizer(id: String): KtorResponse<Organizer> = stub()
    override suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>> = stub()
    override suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit> = stub()
    override suspend fun getComments(eventId: String): KtorResponse<List<EventComment>> = stub()
    override suspend fun addComment(eventId: String, body: String, parentId: String?): KtorResponse<EventComment> = stub()
    override suspend fun toggleCommentLike(commentId: String): KtorResponse<Int> = stub()
}
