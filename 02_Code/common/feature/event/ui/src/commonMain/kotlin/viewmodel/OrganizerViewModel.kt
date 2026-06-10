package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import com.eventmap.event.models.Organizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrganizerViewModel(
    private val repository: EventRepository,
) : BaseScreenModel() {

    private val _organizer = MutableStateFlow<Organizer?>(null)
    val organizer: StateFlow<Organizer?> = _organizer.asStateFlow()

    private val _events = MutableStateFlow<List<EventDetail>>(emptyList())
    val events: StateFlow<List<EventDetail>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadedId: String? = null

    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        screenModelScope.launch {
            _loading.value = true
            _organizer.value = (repository.getOrganizer(id) as? KtorResponse.Success)?.data ?: sampleOrganizer(id)
            _events.value = (repository.getOrganizerEvents(id) as? KtorResponse.Success)?.data ?: sampleEvents(id)
            _loading.value = false
        }
    }

    fun toggleSubscribe() {
        val org = _organizer.value ?: return
        val newState = !org.isSubscribed
        _organizer.value = org.copy(
            isSubscribed = newState,
            subscribers = org.subscribers + if (newState) 1 else -1,
        )
        screenModelScope.launch { repository.toggleSubscribe(org.id, newState) }
    }

    private fun sampleOrganizer(id: String) = Organizer(
        id = id, name = "Astana Live", bio = "Организуем концерты и опен-эйры в центре Астаны",
        isVerified = true, eventsCount = 12, subscribers = 1840, isSubscribed = false,
    )

    private fun sampleEvents(organizerId: String) = listOf(
        EventDetail(
            id = "1", title = "Концерт у Байтерека", category = "Концерт", status = "live",
            location = EventLocation(name = "Байтерек, Астана"), price = EventPrice("paid", 3000, "KZT"),
            organizerId = organizerId, organizerName = "Astana Live",
        ),
        EventDetail(
            id = "10", title = "Опен-эйр у фонтана", category = "Концерт", status = "soon",
            location = EventLocation(name = "Площадь Республики"), price = EventPrice("free"),
            organizerId = organizerId, organizerName = "Astana Live",
        ),
    )
}
