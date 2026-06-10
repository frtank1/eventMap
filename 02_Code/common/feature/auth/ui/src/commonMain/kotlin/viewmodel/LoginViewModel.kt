package com.eventmap.auth.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.auth.AuthRepository
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
) : BaseScreenModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            emitError(ErrorModel(customMessage = "Введите email и пароль"))
            return
        }
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.login(email.trim(), password)) {
                is KtorResponse.Success -> {
                    _loading.value = false
                    _success.value = true
                }
                is KtorResponse.Failure -> {
                    _loading.value = false
                    emitError(r.error)
                }
            }
        }
    }
}
