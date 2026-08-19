package com.eventmap.profile.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.datastore.PulseSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val pushNotifications: Boolean = true,
    val emailNotifications: Boolean = false,
    val publicProfile: Boolean = true,
    val sharingEnabled: Boolean = false,
)

class SettingsViewModel(
    private val pulseSettings: PulseSettings,
    private val httpClient: HttpClient,
) : BaseScreenModel() {

    private val _state = MutableStateFlow(SettingsState(sharingEnabled = pulseSettings.isSharingEnabled()))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setPush(v: Boolean) { _state.value = _state.value.copy(pushNotifications = v) }
    fun setEmail(v: Boolean) { _state.value = _state.value.copy(emailNotifications = v) }
    fun setPublic(v: Boolean) { _state.value = _state.value.copy(publicProfile = v) }

    // Sharing = согласие на гео-пинг «Пульса города» + позицию друзьям (I2) — персистится
    fun setSharing(v: Boolean) {
        pulseSettings.setSharingEnabled(v)
        _state.value = _state.value.copy(sharingEnabled = v)
        if (!v) {
            // немедленно убираем именную позицию с карт друзей (V12: «выключение — DELETE»);
            // подстраховка — ретрай в пинг-цикле карты
            screenModelScope.launch {
                runCatching { httpClient.delete("friends/location") }
            }
        }
    }
}
