package com.eventmap.feed.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.feed.FeedRepository
import com.eventmap.feed.models.SubscriptionItem
import com.eventmap.feed.models.SubscriptionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SubTab(val api: String, val label: String) {
    Organizers("organizers", "Организаторы"),
    Genres("genres", "Жанры"),
    People("people", "Люди"),
}

class SubscriptionsViewModel(
    private val repository: FeedRepository,
) : BaseScreenModel() {

    private val _tab = MutableStateFlow(SubTab.Organizers)
    val tab: StateFlow<SubTab> = _tab.asStateFlow()

    private val _items = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    val items: StateFlow<List<SubscriptionItem>> = _items.asStateFlow()

    private val _requests = MutableStateFlow<List<SubscriptionRequest>>(emptyList())
    val requests: StateFlow<List<SubscriptionRequest>> = _requests.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        loadTab(SubTab.Organizers)
        loadRequests()
    }

    fun setTab(t: SubTab) {
        _tab.value = t
        loadTab(t)
    }

    fun reload() { loadTab(_tab.value); loadRequests() }

    private fun loadTab(t: SubTab) {
        _items.value = emptyList()
        clearError()
        screenModelScope.launch {
            _loading.value = true
            val result = repository.getSubscriptions(t.api)
            if (_tab.value != t) return@launch
            when (result) {
                is KtorResponse.Success -> _items.value = result.data
                is KtorResponse.Failure -> emitError(result.error)
            }
            _loading.value = false
        }
    }

    private fun loadRequests() {
        screenModelScope.launch {
            when (val result = repository.getRequests()) {
                is KtorResponse.Success -> _requests.value = result.data
                is KtorResponse.Failure -> emitError(result.error)
            }
        }
    }

    fun approve(id: String) {
        respond(id, true)
    }

    fun reject(id: String) {
        respond(id, false)
    }

    private val responding = mutableSetOf<String>()
    private fun respond(id: String, approve: Boolean) {
        if (!responding.add(id)) return
        screenModelScope.launch {
            try {
                when (val result = if (approve) repository.approveRequest(id) else repository.rejectRequest(id)) {
                    is KtorResponse.Success -> _requests.value = _requests.value.filterNot { it.id == id }
                    is KtorResponse.Failure -> emitError(result.error)
                }
            } finally { responding.remove(id) }
        }
    }

}
