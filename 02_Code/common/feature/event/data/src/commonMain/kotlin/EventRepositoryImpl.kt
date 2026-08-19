package com.eventmap.event.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.event.EventRepository
import com.eventmap.event.data.ktor.ConfirmMediaRequest
import com.eventmap.event.data.ktor.CreateEventRequest
import com.eventmap.event.data.ktor.CreateVideoRequest
import com.eventmap.event.data.ktor.EventRemoteDataSource
import com.eventmap.event.data.ktor.MediaRemoteDataSource
import com.eventmap.event.data.ktor.PresignRequest
import com.eventmap.event.data.ktor.PresignResponse
import com.eventmap.event.data.ktor.AddCommentRequest
import com.eventmap.event.data.ktor.RatingRequest
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventComment
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket
import com.eventmap.event.models.UploadedMedia
import com.eventmap.event.EventSignalResult
import com.eventmap.event.data.ktor.EventSignalResponse
import com.eventmap.event.data.ktor.JoinEventResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class EventRepositoryImpl(
    private val remote: EventRemoteDataSource,
    private val media: MediaRemoteDataSource,
) : EventRepository {

    override suspend fun getEvents(): KtorResponse<List<EventDetail>> =
        safeRequest { remote.getEvents() }

    override suspend fun getEvent(id: String): KtorResponse<EventDetail> =
        safeRequest { remote.getEvent(id) }

    override suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>> =
        safeRequest { remote.getParticipants(id, type) }

    // Бэк шлёт kind ("image"|"video"), UI-модель ждёт type ("photo"|"video") — маппим явно,
    // иначе все ролики парсились бы как фото (kind терялся на ignoreUnknownKeys).
    override suspend fun getMedia(id: String): KtorResponse<List<MediaItem>> =
        when (val r = safeRequest<List<com.eventmap.event.data.ktor.MediaDto>> { remote.getMedia(id) }) {
            is KtorResponse.Success -> KtorResponse.Success(
                r.data.map {
                    MediaItem(
                        id = it.id,
                        url = it.url,
                        type = if (it.kind == "video") "video" else "photo",
                    )
                },
            )
            is KtorResponse.Failure -> r
        }

    override suspend fun attend(id: String): KtorResponse<Unit> =
        unitRequest { remote.attend(id) }

    override suspend fun toggleSignal(id: String, type: String): KtorResponse<EventSignalResult> =
        when (val r = safeRequest<EventSignalResponse> { remote.toggleSignal(id, type) }) {
            is KtorResponse.Success -> KtorResponse.Success(EventSignalResult(r.data.enabled, r.data.count))
            is KtorResponse.Failure -> r
        }

    override suspend fun joinEvent(id: String, message: String): KtorResponse<String> =
        when (val r = safeRequest<JoinEventResponse> { remote.joinEvent(id, message) }) {
            is KtorResponse.Success -> KtorResponse.Success(r.data.status)
            is KtorResponse.Failure -> r
        }

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
                    kind = draft.kind,
                    sourceType = draft.sourceType,
                    visibility = draft.visibility,
                    joinPolicy = draft.joinPolicy,
                    audience = draft.audience,
                    minAge = draft.minAge,
                    expiresAtMs = draft.expiresAtMs,
                    proofMediaId = draft.proofMediaId,
                )
            )
        }

    override suspend fun myEvents(): KtorResponse<List<EventDetail>> =
        safeRequest { remote.myEvents() }

    override suspend fun friendsGoing(id: String): KtorResponse<com.eventmap.event.models.FriendsGoing> =
        safeRequest { remote.friendsGoing(id) }

    override suspend fun updateEvent(id: String, draft: CreateEventDraft): KtorResponse<EventDetail> =
        safeRequest {
            remote.updateEvent(
                id,
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
                    kind = draft.kind,
                    sourceType = draft.sourceType,
                    visibility = draft.visibility,
                    joinPolicy = draft.joinPolicy,
                    audience = draft.audience,
                    minAge = draft.minAge,
                    expiresAtMs = draft.expiresAtMs,
                    proofMediaId = draft.proofMediaId,
                )
            )
        }

    override suspend fun deleteEvent(id: String): KtorResponse<Unit> =
        unitRequest { remote.deleteEvent(id) }

    override suspend fun uploadMedia(
        bytes: ByteArray,
        contentType: String,
        kind: String,
        eventId: String?,
        onProgress: ((Float) -> Unit)?,
    ): KtorResponse<UploadedMedia> {
        // 1) presign
        val presigned = when (val r = safeRequest<PresignResponse> {
            media.presign(PresignRequest(kind = kind, contentType = contentType))
        }) {
            is KtorResponse.Success -> r.data
            is KtorResponse.Failure -> return KtorResponse.Failure(r.error)  // в т.ч. 503 STORAGE_OFF
        }

        // 2) прямой PUT файла в storage (без bearer)
        val putResp = try {
            media.putFile(presigned.uploadUrl, bytes, contentType, onProgress)
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!putResp.status.isSuccess()) return KtorResponse.failure<UploadedMedia>(putResp)

        // 3) confirm — сохраняем метаданные
        return when (val r = safeRequest<com.eventmap.event.data.ktor.MediaDto> {
            media.confirm(
                ConfirmMediaRequest(
                    mediaId = presigned.mediaId,
                    url = presigned.publicUrl,
                    contentType = contentType,
                    kind = kind,
                    eventId = eventId,
                )
            )
        }) {
            is KtorResponse.Success -> KtorResponse.Success(
                UploadedMedia(mediaId = presigned.mediaId, url = presigned.publicUrl)
            )
            is KtorResponse.Failure -> KtorResponse.Failure(r.error)
        }
    }

    override suspend fun registerReel(
        eventId: String,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
    ): KtorResponse<Unit> =
        unitRequest {
            media.createVideo(
                CreateVideoRequest(
                    mediaUrl = mediaUrl,
                    eventId = eventId,
                    thumb = thumb,
                    caption = caption,
                    category = category,
                )
            )
        }

    override suspend fun purchase(id: String): KtorResponse<Ticket> =
        safeRequest { remote.purchase(id) }

    override suspend fun getRatings(id: String): KtorResponse<List<Rating>> =
        safeRequest { remote.getRatings(id) }

    override suspend fun rateEvent(id: String, value: Int, review: String, tags: List<String>): KtorResponse<Unit> =
        unitRequest { remote.rateEvent(id, RatingRequest(value, review, tags)) }

    override suspend fun getOrganizer(id: String): KtorResponse<Organizer> =
        safeRequest { remote.getOrganizer(id) }

    override suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>> =
        safeRequest { remote.getOrganizerEvents(id) }

    override suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit> =
        unitRequest { if (subscribe) remote.subscribe(organizerId) else remote.unsubscribe(organizerId) }

    // D9 (§8.6): комментарии события
    override suspend fun getComments(eventId: String): KtorResponse<List<EventComment>> =
        safeRequest { remote.getComments(eventId) }

    override suspend fun addComment(eventId: String, body: String, parentId: String?): KtorResponse<EventComment> =
        safeRequest { remote.addComment(eventId, AddCommentRequest(body, parentId)) }

    override suspend fun toggleCommentLike(commentId: String): KtorResponse<Int> =
        when (val r = safeRequest<LikeResponse> { remote.likeComment(commentId) }) {
            is KtorResponse.Success -> KtorResponse.Success(r.data.likeCount)
            is KtorResponse.Failure -> r
        }

    @kotlinx.serialization.Serializable
    private data class LikeResponse(val likeCount: Int = 0)

    private suspend inline fun unitRequest(call: () -> HttpResponse): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
