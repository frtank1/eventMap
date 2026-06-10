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

    private fun loadTab(t: SubTab) {
        screenModelScope.launch {
            _loading.value = true
            _items.value = (repository.getSubscriptions(t.api) as? KtorResponse.Success)?.data ?: sample(t)
            _loading.value = false
        }
    }

    private fun loadRequests() {
        screenModelScope.launch {
            _requests.value = (repository.getRequests() as? KtorResponse.Success)?.data ?: sampleRequests()
        }
    }

    fun approve(id: String) {
        _requests.value = _requests.value.filterNot { it.id == id }
        screenModelScope.launch { repository.approveRequest(id) }
    }

    fun reject(id: String) {
        _requests.value = _requests.value.filterNot { it.id == id }
        screenModelScope.launch { repository.rejectRequest(id) }
    }

    private fun sample(t: SubTab): List<SubscriptionItem> = when (t) {
        SubTab.Organizers -> listOf(
            SubscriptionItem("o1", "Astana Live", "organizers", "12 событий", "🎤"),
            SubscriptionItem("o2", "City Yoga", "organizers", "8 событий", "🧘"),
            SubscriptionItem("o3", "QuizClub", "organizers", "5 событий", "🧩"),
        )
        SubTab.Genres -> listOf(
            SubscriptionItem("g1", "Концерты", "genres", "320 событий", "🎵"),
            SubscriptionItem("g2", "Спорт", "genres", "210 событий", "⚽"),
        )
        SubTab.People -> listOf(
            SubscriptionItem("u1", "Айдана", "people", "@aidana", "🧑"),
            SubscriptionItem("u2", "Тимур", "people", "@timur", "🧑"),
        )
    }

    private fun sampleRequests() = listOf(
        SubscriptionRequest("r1", "Данияр", "хочет подписаться"),
    )
}
