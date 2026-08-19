package com.eventmap.base_screen

import cafe.adriel.voyager.core.model.ScreenModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.eventmap.core.network.models.ErrorModel

abstract class BaseScreenModel : ScreenModel {
    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage

    protected fun emitError(error: ErrorModel) {
        // customMessage (валидация клиента) → message → вложенный серверный error.message
        val msg = error.customMessage ?: error.message ?: error.error?.message ?: "Неизвестная ошибка"
        _errorMessage.tryEmit(msg)
    }
}