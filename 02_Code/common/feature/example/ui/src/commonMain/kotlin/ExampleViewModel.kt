package com.eventmap.example.ui

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.example.ui.domain.LoginUseCase
import com.eventmap.example.ui.models.ExampleEvent
import com.eventmap.example.ui.models.NavigationEvent

interface IExampleViewModel {
    val state: StateFlow<ExampleState>
    val navigationEvent: StateFlow<NavigationEvent>
    val errorMessage: SharedFlow<String>
    fun sendEvent(event: ExampleEvent)
}

sealed class ExampleState {
    data object Default : ExampleState()
    data object Loading : ExampleState()
}

class ExampleViewModel(
    private val loginUseCase: LoginUseCase
) : BaseScreenModel(), IExampleViewModel {

    private val _state = MutableStateFlow<ExampleState>(ExampleState.Default)
    override val state: StateFlow<ExampleState> = _state.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent>(NavigationEvent.Default)
    override val navigationEvent: StateFlow<NavigationEvent> = _navigationEvent.asStateFlow()

    override fun sendEvent(event: ExampleEvent) {
        when (event) {
            is ExampleEvent.Back -> Unit
            is ExampleEvent.Login -> login(event.login, event.password)
        }
    }

    private fun login(login: String, password: String) {
        screenModelScope.launch {
            _state.value = ExampleState.Loading
            when (val result = loginUseCase(login, password)) {
                is KtorResponse.Success -> _state.value = ExampleState.Default
                is KtorResponse.Failure -> {
                    _state.value = ExampleState.Default
                    emitError(result.error)   // ← бизнес-ошибка уходит в снек экрана
                }
            }
        }
    }
}
