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
            clearError()
            try {
                when (val result = repository.getMe()) {
                    is KtorResponse.Success -> {
                        if (_profile.value?.id != result.data.id) _history.value = emptyList()
                        _profile.value = result.data
                        when (val history = repository.getHistory(result.data.id)) {
                            is KtorResponse.Success -> _history.value = history.data
                            is KtorResponse.Failure -> emitError(history.error)
                        }
                    }
                    is KtorResponse.Failure -> {
                        _profile.value = null
                        _history.value = emptyList()
                        emitError(result.error)
                    }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() {
        repository.logout()
        _loggedOut.value = true
    }

}
