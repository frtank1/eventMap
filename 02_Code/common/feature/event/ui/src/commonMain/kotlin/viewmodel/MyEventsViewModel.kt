package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// «Мои события» — созданные мной (GET /events/my), правка и удаление.
class MyEventsViewModel(private val repository: EventRepository) : ScreenModel {

    private val _events = MutableStateFlow<List<EventDetail>>(emptyList())
    val events: StateFlow<List<EventDetail>> = _events.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val r = repository.myEvents()) {
                is KtorResponse.Success -> _events.value = r.data.sortedBy { it.startAt }
                is KtorResponse.Failure -> _error.value = "Не удалось загрузить — проверь сеть"
            }
            _loading.value = false
        }
    }

    fun delete(id: String) {
        screenModelScope.launch {
            when (repository.deleteEvent(id)) {
                is KtorResponse.Success -> _events.value = _events.value.filterNot { it.id == id }
                is KtorResponse.Failure -> _error.value = "Не удалось удалить — попробуй ещё раз"
            }
        }
    }
}
