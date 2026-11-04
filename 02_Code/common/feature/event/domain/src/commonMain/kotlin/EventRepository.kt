package com.eventmap.event

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventComment
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Organizer
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import com.eventmap.event.models.Ticket
import com.eventmap.event.models.UploadedMedia

interface EventRepository {
    suspend fun getEvents(): KtorResponse<List<EventDetail>>
    suspend fun getEvent(id: String): KtorResponse<EventDetail>
    suspend fun getParticipants(id: String, type: String): KtorResponse<List<Participant>>
    suspend fun getMedia(id: String): KtorResponse<List<MediaItem>>
    suspend fun attend(id: String): KtorResponse<Unit>
    suspend fun toggleSignal(id: String, type: String): KtorResponse<EventSignalResult>
    suspend fun joinEvent(id: String, message: String = ""): KtorResponse<String>
    suspend fun createEvent(draft: CreateEventDraft): KtorResponse<EventDetail>
    /** Мои события (создатель = я): GET /events/my */
    suspend fun myEvents(): KtorResponse<List<EventDetail>>
    /** «N друзей уже там»: мои друзья среди участников события */
    suspend fun friendsGoing(id: String): KtorResponse<com.eventmap.event.models.FriendsGoing>
    /** Редактирование своего события: PATCH /events/{id} */
    suspend fun updateEvent(id: String, draft: CreateEventDraft): KtorResponse<EventDetail>
    /** Удаление своего события: DELETE /events/{id} */
    suspend fun deleteEvent(id: String): KtorResponse<Unit>

    /**
     * Загрузка медиа в object storage (R2): presign → PUT → confirm.
     * @param kind "image" (обложка) | "video" (ролик).
     * @param eventId привязка к событию (для обложки — null, цепляется через coverMediaId).
     */
    suspend fun uploadMedia(
        bytes: ByteArray,
        contentType: String,
        kind: String,
        eventId: String?,
        onProgress: ((Float) -> Unit)? = null, // 0..1 — прогресс PUT в storage
    ): KtorResponse<UploadedMedia>

    /** Регистрация ролика (рилса) после загрузки файла: POST /videos. */
    suspend fun registerReel(
        eventId: String,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
        mediaId: String? = null,
    ): KtorResponse<Unit>
    // P (ADR-002): билеты и рейтинги
    suspend fun purchase(id: String): KtorResponse<Ticket>
    suspend fun getRatings(id: String): KtorResponse<List<Rating>>
    suspend fun rateEvent(id: String, value: Int, review: String, tags: List<String> = emptyList()): KtorResponse<Unit>
    // Организаторы
    suspend fun getOrganizer(id: String): KtorResponse<Organizer>
    suspend fun getOrganizerEvents(id: String): KtorResponse<List<EventDetail>>
    suspend fun toggleSubscribe(organizerId: String, subscribe: Boolean): KtorResponse<Unit>

    // D9 (§8.6): комментарии события
    suspend fun getComments(eventId: String): KtorResponse<List<EventComment>>
    suspend fun addComment(eventId: String, body: String, parentId: String? = null): KtorResponse<EventComment>
    /** @return новое количество лайков */
    suspend fun toggleCommentLike(commentId: String): KtorResponse<Int>
}

data class EventSignalResult(val enabled: Boolean, val count: Int)
