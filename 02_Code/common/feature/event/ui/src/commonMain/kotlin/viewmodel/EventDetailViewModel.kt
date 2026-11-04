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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class EventDetailViewModel(
    private val repository: EventRepository,
    // null — в тестах/на платформах без нотификаций
    private val reminders: com.eventmap.core.platform.reminders.LocalReminders? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
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
    private var loadJob: Job? = null
    private var attending = false
    private var refreshingMedia = false

    /** Called by visible media screens; refreshes processing status and expiring signed URLs. */
    fun refreshMedia() {
        val id = loadedId ?: return
        if (refreshingMedia) return
        refreshingMedia = true
        screenModelScope.launch(dispatcher) {
            try {
                when (val result = repository.getMedia(id)) {
                    is KtorResponse.Success -> if (loadedId == id) _media.value = result.data
                    is KtorResponse.Failure -> if (loadedId == id) emitError(result.error)
                }
            } finally { refreshingMedia = false }
        }
    }

    fun reload() { loadedId?.let { load(it, force = true) } }

    fun load(id: String, force: Boolean = false) {
        if (!force && loadedId == id && _event.value != null) return
        loadJob?.cancel()
        loadedId = id
        _event.value = null
        _participants.value = emptyList()
        _media.value = emptyList()
        _ratings.value = emptyList()
        _friendsGoing.value = null
        _attended.value = false
        _interested.value = false
        _confirmed.value = false
        _joinStatus.value = ""
        _ticketStatus.value = "none"
        _ratingSent.value = false
        loadJob = screenModelScope.launch(dispatcher) {
            clearError()
            _loading.value = true
            when (val result = repository.getEvent(id)) {
                is KtorResponse.Success -> _event.value = result.data
                is KtorResponse.Failure -> {
                    emitError(result.error)
                    _loading.value = false
                    return@launch
                }
            }
            _participants.value = repository.getParticipants(id, "going").dataOrError().orEmpty()
            _friendsGoing.value = (repository.friendsGoing(id) as? KtorResponse.Success)?.data
            _media.value = repository.getMedia(id).dataOrError().orEmpty()
            _ratings.value = repository.getRatings(id).dataOrError().orEmpty()
            _loading.value = false
        }
    }

    fun purchase() {
        if (_purchasing.value || _event.value == null) return
        val id = loadedId ?: return
        _purchasing.value = true
        screenModelScope.launch(dispatcher) {
            when (val r = repository.purchase(id)) {
                is KtorResponse.Success -> {
                    _purchasing.value = false
                    if (loadedId == id) {
                        _ticketStatus.value = r.data.status.ifBlank { "none" }
                        if (r.data.status == "paid") scheduleReminder()
                    }
                }
                is KtorResponse.Failure -> {
                    _purchasing.value = false
                    emitError(r.error)
                }
            }
        }
    }

    fun submitRating(value: Int, review: String) {
        if (_event.value == null) return
        val id = loadedId ?: return
        screenModelScope.launch(dispatcher) {
            when (val r = repository.rateEvent(id, value, review)) {
                is KtorResponse.Success -> if (loadedId == id) markRated(value, review)
                is KtorResponse.Failure -> emitError(r.error)
            }
        }
    }

    private fun markRated(value: Int, review: String) {
        _ratings.value = listOf(Rating(value, review, "Вы", "только что")) + _ratings.value
        _ratingSent.value = true
    }


    fun attend() {
        if (attending || _event.value == null) return
        val id = loadedId ?: return
        attending = true
        screenModelScope.launch(dispatcher) {
          try {
            when (val r = repository.attend(id)) {
                is KtorResponse.Success -> {
                    if (loadedId == id) {
                        _attended.value = true
                        scheduleReminder()
                    }
                }
                is KtorResponse.Failure -> {
                    emitError(r.error)
                }
            }
          } finally { attending = false }
        }
    }

    fun toggleInterest() = toggleSignal("interest")

    fun confirmHere() = toggleSignal("confirm")

    private fun toggleSignal(type: String) {
        if (_event.value == null) return
        val id = loadedId ?: return
        screenModelScope.launch(dispatcher) {
            when (val r = repository.toggleSignal(id, type)) {
                is KtorResponse.Success -> {
                    if (loadedId != id) return@launch
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
        if (_event.value == null) return
        val id = loadedId ?: return
        screenModelScope.launch(dispatcher) {
            when (val r = repository.joinEvent(id, message)) {
                is KtorResponse.Success -> if (loadedId == id) _joinStatus.value = r.data
                is KtorResponse.Failure -> emitError(r.error)
            }
        }
    }

    private fun <T> KtorResponse<T>.dataOrError(): T? = when (this) {
        is KtorResponse.Success -> data
        is KtorResponse.Failure -> { emitError(error); null }
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

}
