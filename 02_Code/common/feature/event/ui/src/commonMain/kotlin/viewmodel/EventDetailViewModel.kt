package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Participant
import com.eventmap.event.models.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventDetailViewModel(
    private val repository: EventRepository,
    // null — в тестах/на платформах без нотификаций
    private val reminders: com.eventmap.core.platform.reminders.LocalReminders? = null,
) : BaseScreenModel() {

    private val _event = MutableStateFlow<EventDetail?>(null)
    val event: StateFlow<EventDetail?> = _event.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    // «N друзей уже там» — null при офлайне/госте (чип просто не показываем)
    private val _friendsGoing = MutableStateFlow<com.eventmap.event.models.FriendsGoing?>(null)
    val friendsGoing: StateFlow<com.eventmap.event.models.FriendsGoing?> = _friendsGoing.asStateFlow()

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _attended = MutableStateFlow(false)
    val attended: StateFlow<Boolean> = _attended.asStateFlow()

    private val _interested = MutableStateFlow(false)
    val interested: StateFlow<Boolean> = _interested.asStateFlow()

    private val _confirmed = MutableStateFlow(false)
    val confirmed: StateFlow<Boolean> = _confirmed.asStateFlow()

    private val _joinStatus = MutableStateFlow("")
    val joinStatus: StateFlow<String> = _joinStatus.asStateFlow()

    private val _ticketStatus = MutableStateFlow("none")   // none | pending | paid
    val ticketStatus: StateFlow<String> = _ticketStatus.asStateFlow()

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    private val _ratings = MutableStateFlow<List<Rating>>(emptyList())
    val ratings: StateFlow<List<Rating>> = _ratings.asStateFlow()

    private val _ratingSent = MutableStateFlow(false)
    val ratingSent: StateFlow<Boolean> = _ratingSent.asStateFlow()

    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        screenModelScope.launch {
            _loading.value = true
            // Бэкенд может быть недоступен — показываем демо-данные (fallback).
            _event.value = (repository.getEvent(id) as? KtorResponse.Success)?.data ?: sampleEvent(id)
            _participants.value = (repository.getParticipants(id, "going") as? KtorResponse.Success)?.data ?: sampleParticipants()
            _friendsGoing.value = (repository.friendsGoing(id) as? KtorResponse.Success)?.data
            _media.value = (repository.getMedia(id) as? KtorResponse.Success)?.data ?: sampleMedia()
            _ratings.value = (repository.getRatings(id) as? KtorResponse.Success)?.data ?: sampleRatings()
            _loading.value = false
        }
    }

    fun purchase() {
        val id = loadedId ?: return
        screenModelScope.launch {
            _purchasing.value = true
            when (val r = repository.purchase(id)) {
                is KtorResponse.Success -> {
                    _purchasing.value = false
                    _ticketStatus.value = r.data.status.ifBlank { "paid" }
                    scheduleReminder() // билет = идёшь → напомним за час
                }
                is KtorResponse.Failure -> {
                    _purchasing.value = false
                    _ticketStatus.value = "paid" // без бэка — считаем оформленным
                    emitError(r.error)
                }
            }
        }
    }

    fun submitRating(value: Int, review: String) {
        val id = loadedId ?: return
        screenModelScope.launch {
            when (val r = repository.rateEvent(id, value, review)) {
                is KtorResponse.Success -> markRated(value, review)
                is KtorResponse.Failure -> { markRated(value, review); emitError(r.error) }
            }
        }
    }

    private fun markRated(value: Int, review: String) {
        _ratings.value = listOf(Rating(value, review, "Вы", "только что")) + _ratings.value
        _ratingSent.value = true
    }

    private fun sampleRatings() = listOf(
        Rating(5, "Огонь, приду ещё!", "Айдана", "вчера"),
        Rating(4, "Хорошо, но людно", "Тимур", "2 дня назад"),
    )

    fun attend() {
        val id = loadedId ?: return
        screenModelScope.launch {
            when (val r = repository.attend(id)) {
                is KtorResponse.Success -> {
                    _attended.value = true
                    scheduleReminder() // «иду» → напомним за час до старта
                }
                is KtorResponse.Failure -> {
                    // офлайн/без бэка — всё равно отметим локально
                    _attended.value = true
                    emitError(r.error)
                }
            }
        }
    }

    fun toggleInterest() = toggleSignal("interest")

    fun confirmHere() = toggleSignal("confirm")

    private fun toggleSignal(type: String) {
        val id = loadedId ?: return
        screenModelScope.launch {
            when (val r = repository.toggleSignal(id, type)) {
                is KtorResponse.Success -> {
                    if (type == "interest") _interested.value = r.data.enabled else _confirmed.value = r.data.enabled
                    _event.value = _event.value?.let {
                        if (type == "interest") it.copy(interestCount = r.data.count)
                        else it.copy(confirmationCount = r.data.count)
                    }
                }
                is KtorResponse.Failure -> emitError(r.error)
            }
        }
    }

    fun join(message: String = "") {
        val id = loadedId ?: return
        screenModelScope.launch {
            when (val r = repository.joinEvent(id, message)) {
                is KtorResponse.Success -> _joinStatus.value = r.data
                is KtorResponse.Failure -> emitError(r.error)
            }
        }
    }

    // Локальное напоминание за час до старта; прошедшее/непарсибельное время — игнор
    private fun scheduleReminder() {
        val e = _event.value ?: return
        val startMs = runCatching { kotlin.time.Instant.parse(e.startAt).toEpochMilliseconds() }.getOrNull() ?: return
        reminders?.schedule(
            id = "event-${e.id}",
            title = "Через час: ${e.title}",
            body = listOfNotNull(
                e.location.name.takeIf { it.isNotBlank() },
                e.price.label(),
            ).joinToString(" · "),
            atEpochMs = startMs - 60 * 60 * 1000,
        )
    }

    private fun sampleEvent(id: String) = EventDetail(
        id = id,
        title = "Концерт у Байтерека",
        description = "Indie-вечер под открытым небом: живая музыка, фудкорт и тёплая атмосфера старого центра. Приходи один или с компанией — будет душевно.",
        category = "Концерт",
        status = "live",
        startAt = "2026-06-08T18:00:00Z",
        endAt = "2026-06-08T23:00:00Z",
        location = EventLocation(51.1283, 71.4306, "Байтерек, Астана"),
        price = EventPrice("paid", 3000, "KZT"),
        goingCount = 48,
        liveViewers = 128,
        ratingAvg = 4.6,
        ratingCount = 128,
        organizerId = "o1",
        organizerName = "Astana Live",
    )

    private fun sampleParticipants() = listOf(
        Participant("u1", "Айдана", type = "going"),
        Participant("u2", "Тимур", type = "going"),
        Participant("u3", "Мария", type = "going"),
        Participant("u4", "Данияр", type = "going"),
        Participant("u5", "Алия", type = "going"),
    )

    private fun sampleMedia() = listOf(
        MediaItem("m1", "", "photo", "Айдана"),
        MediaItem("m2", "", "video", "Тимур"),
        MediaItem("m3", "", "photo", "Мария"),
        MediaItem("m4", "", "photo", "Данияр"),
    )
}
