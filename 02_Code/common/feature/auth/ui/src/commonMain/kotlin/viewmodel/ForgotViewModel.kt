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

class ForgotViewModel(
    private val repository: AuthRepository,
) : BaseScreenModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent.asStateFlow()

    fun submit(email: String) {
        if (!email.contains("@")) {
            emitError(ErrorModel(customMessage = "Некорректный email"))
            return
        }
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.forgotPassword(email.trim())) {
                is KtorResponse.Success -> {
                    _loading.value = false
                    _sent.value = true
                }
                is KtorResponse.Failure -> {
                    _loading.value = false
                    emitError(r.error)
                }
            }
        }
    }
}
