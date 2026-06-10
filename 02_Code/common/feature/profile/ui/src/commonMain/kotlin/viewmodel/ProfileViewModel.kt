package com.eventmap.profile.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.profile.ProfileRepository
import com.eventmap.profile.models.HistoryItem
import com.eventmap.profile.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository,
) : BaseScreenModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            val me = (repository.getMe() as? KtorResponse.Success)?.data ?: sampleProfile()
            _profile.value = me
            _history.value = (repository.getHistory(me.id) as? KtorResponse.Success)?.data ?: sampleHistory()
            _loading.value = false
        }
    }

    fun logout() {
        repository.logout()
        _loggedOut.value = true
    }

    private fun sampleProfile() = UserProfile(
        id = "me", name = "Азамат", username = "@azamat", bio = "Люблю концерты и спорт в Астане",
        subscriptions = 34, subscribers = 12, eventsCount = 3, isOrganizer = false,
    )

    private fun sampleHistory() = listOf(
        HistoryItem("1", "Концерт у Байтерека", "8 июня", "Концерт"),
        HistoryItem("2", "Кайрат — Астана", "1 июня", "Спорт"),
        HistoryItem("3", "Квиз «Мозгобойня»", "25 мая", "Квиз"),
    )
}
