package com.eventmap.video.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Модель состояния экрана записи рилса
// ─────────────────────────────────────────────────────────────────────────────

enum class RecordPhase { Recording, Preview }

data class RecordVideoState(
    val phase: RecordPhase = RecordPhase.Recording,
    val progress: Float = 0f,          // 0..1 (макс 60 с)
    val elapsedSec: Int = 0,
    val caption: String = "",
    val linkedEventTitle: String = "",
    val publishing: Boolean = false,
    val flashOn: Boolean = false,
    val frontCamera: Boolean = false,
)

class RecordVideoViewModel : BaseScreenModel() {

    private val _state = MutableStateFlow(RecordVideoState())
    val state: StateFlow<RecordVideoState> = _state.asStateFlow()

    private val maxDurationSec = 60

    init {
        startTimer()
    }

    // Тикер — имитирует запись
    private fun startTimer() {
        screenModelScope.launch {
            while (_state.value.phase == RecordPhase.Recording && _state.value.elapsedSec < maxDurationSec) {
                delay(1_000)
                val elapsed = _state.value.elapsedSec + 1
                _state.value = _state.value.copy(
                    elapsedSec = elapsed,
                    progress = elapsed.toFloat() / maxDurationSec,
                )
                if (elapsed >= maxDurationSec) stopRecording()
            }
        }
    }

    fun stopRecording() {
        _state.value = _state.value.copy(phase = RecordPhase.Preview)
    }

    fun flipCamera() {
        _state.value = _state.value.copy(frontCamera = !_state.value.frontCamera)
    }

    fun toggleFlash() {
        _state.value = _state.value.copy(flashOn = !_state.value.flashOn)
    }

    fun setCaption(text: String) {
        _state.value = _state.value.copy(caption = text)
    }

    fun publish(onDone: () -> Unit) {
        screenModelScope.launch {
            _state.value = _state.value.copy(publishing = true)
            delay(1_200) // имитация загрузки
            _state.value = _state.value.copy(publishing = false)
            onDone()
        }
    }

    fun discard() {
        _state.value = RecordVideoState()
    }
}
