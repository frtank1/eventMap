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

class RegisterViewModel(
    private val repository: AuthRepository,
) : BaseScreenModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun register(name: String, email: String, password: String, confirm: String) {
        when {
            name.isBlank() -> emitError(ErrorModel(customMessage = "Введите имя"))
            !email.contains("@") -> emitError(ErrorModel(customMessage = "Некорректный email"))
            password.length < 6 -> emitError(ErrorModel(customMessage = "Пароль минимум 6 символов"))
            password != confirm -> emitError(ErrorModel(customMessage = "Пароли не совпадают"))
            else -> doRegister(name.trim(), email.trim(), password)
        }
    }

    private fun doRegister(name: String, email: String, password: String) {
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.register(email, password, name)) {
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
