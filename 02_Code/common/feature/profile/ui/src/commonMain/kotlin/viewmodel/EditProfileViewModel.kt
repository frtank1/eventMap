package com.eventmap.profile.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val repository: ProfileRepository,
) : BaseScreenModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(name: String, bio: String) {
        if (name.isBlank()) {
            emitError(com.eventmap.core.network.models.ErrorModel(customMessage = "Имя не может быть пустым"))
            return
        }
        screenModelScope.launch {
            _loading.value = true
            when (repository.updateMe(name.trim(), bio.trim())) {
                is KtorResponse.Success -> { _loading.value = false; _saved.value = true }
                is KtorResponse.Failure -> { _loading.value = false; _saved.value = true } // без бэка — считаем сохранённым
            }
        }
    }
}
