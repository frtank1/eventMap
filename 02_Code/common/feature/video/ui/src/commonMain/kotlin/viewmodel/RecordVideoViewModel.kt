package com.eventmap.video.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.VideoRepository
import com.eventmap.views.camera.RecordedVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

// ─────────────────────────────────────────────────────────────────────────────
//  E3/E4 — запись и публикация рилса.
//  Камера отдаёт RecordedVideo (core/ui views/camera) → publishReel
//  (presign → PUT → confirm → POST /videos). Без файла публикация невозможна.
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
    val stopping: Boolean = false,     // ждём финализацию файла камерой
    val errorText: String? = null,
    val uploadProgress: Float = 0f,
    val publishedMessage: String? = null,
)

class RecordVideoViewModel(
    private val repository: VideoRepository,
) : BaseScreenModel() {

    private val _state = MutableStateFlow(RecordVideoState())
    val state: StateFlow<RecordVideoState> = _state.asStateFlow()

    private val maxDurationSec = 60

    /** Файл с камеры; null — камера недоступна (desktop/wasm) или запись сорвалась */
    private var recorded: RecordedVideo? = null

    init {
        startTimer()
    }

    // Таймер записи: прогресс-бар + автостоп на 60 с (сама остановка — на экране, у него камера)
    private fun startTimer() {
        screenModelScope.launch {
            while (_state.value.phase == RecordPhase.Recording && _state.value.elapsedSec < maxDurationSec) {
                delay(1_000)
                if (_state.value.phase != RecordPhase.Recording) return@launch
                val elapsed = _state.value.elapsedSec + 1
                _state.value = _state.value.copy(
                    elapsedSec = elapsed,
                    progress = elapsed.toFloat() / maxDurationSec,
                )
            }
        }
    }

    /** true — пора останавливать запись (автостоп по лимиту) */
    fun reachedLimit(): Boolean = _state.value.elapsedSec >= maxDurationSec

    fun markStopping() {
        _state.value = _state.value.copy(stopping = true)
    }

    /** Файл готов (или null при ошибке камеры) → превью E4 */
    fun onRecorded(video: RecordedVideo?) {
        recorded = video
        _state.value = _state.value.copy(
            phase = RecordPhase.Preview,
            stopping = false,
            errorText = if (video == null) "Камера не сохранила видео. Запишите ролик заново" else null,
        )
    }

    /** file://-ссылка для плеера превью; null — показываем плейсхолдер */
    fun previewUrl(): String? = recorded?.localUrl

    fun flipCamera() {
        _state.value = _state.value.copy(frontCamera = !_state.value.frontCamera)
    }

    fun toggleFlash() {
        _state.value = _state.value.copy(flashOn = !_state.value.flashOn)
    }

    fun setCaption(text: String) {
        _state.value = _state.value.copy(caption = text.take(500))
    }

    fun publish(eventId: String? = null, onDone: () -> Unit) {
        if (_state.value.publishing) return
        if (_state.value.publishedMessage != null) { onDone(); return }
        if (eventId.isNullOrBlank()) {
            _state.value = _state.value.copy(errorText = "Откройте событие и добавьте ролик из его карточки")
            return
        }
        val video = recorded
        if (video == null || video.bytes.isEmpty()) {
            _state.value = _state.value.copy(errorText = "Нет видео для публикации. Запишите ролик заново")
            return
        }
        if (video.bytes.size > 60 * 1024 * 1024) {
            _state.value = _state.value.copy(errorText = "Видео больше 60 МБ. Запишите более короткий ролик")
            return
        }
        _state.value = _state.value.copy(publishing = true, errorText = null, uploadProgress = 0f)
        screenModelScope.launch {
            try {
            when (val r = repository.publishReel(video.bytes, video.contentType, eventId = eventId, caption = _state.value.caption,
                onProgress = { p -> _state.value = _state.value.copy(uploadProgress = p) })) {
                is KtorResponse.Success -> {
                    _state.value = _state.value.copy(publishedMessage = if (r.data.status == "ready")
                        "Ролик опубликован" else "Ролик загружен и обрабатывается. Он появится в событии автоматически")
                }
                is KtorResponse.Failure -> {
                    _state.value = _state.value.copy(
                        publishing = false,
                        errorText = r.error.error?.message ?: r.error.message ?: "Не удалось опубликовать — проверь сеть",
                    )
                }
            }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(errorText = "Не удалось опубликовать. Повторите попытку") }
            finally { _state.value = _state.value.copy(publishing = false) }
        }
    }

    fun discard() {
        recorded = null
        _state.value = RecordVideoState()
        startTimer()
    }
}
