package com.eventmap.profile.ui.viewmodel

import com.eventmap.base_screen.BaseScreenModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val pushNotifications: Boolean = true,
    val emailNotifications: Boolean = false,
    val publicProfile: Boolean = true,
    val sharingEnabled: Boolean = false,
)

class SettingsViewModel : BaseScreenModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setPush(v: Boolean) { _state.value = _state.value.copy(pushNotifications = v) }
    fun setEmail(v: Boolean) { _state.value = _state.value.copy(emailNotifications = v) }
    fun setPublic(v: Boolean) { _state.value = _state.value.copy(publicProfile = v) }
    fun setSharing(v: Boolean) { _state.value = _state.value.copy(sharingEnabled = v) }
}
