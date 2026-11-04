package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.EventRepository
import com.eventmap.event.models.UploadedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddMediaState(val pick: PickedMedia? = null, val caption: String = "", val loading: Boolean = false,
    val progress: Float = 0f, val done: String? = null)

class AddMediaViewModel(private val repository: EventRepository) : BaseScreenModel() {
    private val _state = MutableStateFlow(AddMediaState())
    val state = _state.asStateFlow()
    private var uploaded: UploadedMedia? = null
    fun choose(pick: PickedMedia) { if (!_state.value.loading) { uploaded = null; _state.value = AddMediaState(pick = pick) } }
    fun caption(value: String) { _state.value = _state.value.copy(caption = value.take(500)) }
    fun publish(eventId: String) {
        val s = _state.value
        val pick = s.pick ?: return
        if (s.loading || s.done != null) return
        _state.value = s.copy(loading = true, progress = 0f)
        screenModelScope.launch {
            try {
                clearError()
                val video = pick.contentType.startsWith("video/")
                val media = uploaded ?: when (val r = repository.uploadMedia(pick.bytes, pick.contentType,
                    if (video) "video" else "image", eventId, onProgress = { _state.value = _state.value.copy(progress = it) })) {
                    is KtorResponse.Success -> r.data.also { uploaded = it }
                    is KtorResponse.Failure -> { emitError(r.error); return@launch }
                }
                if (video) when (val r = repository.registerReel(eventId, media.url, null, s.caption, "", media.mediaId)) {
                    is KtorResponse.Success -> Unit
                    is KtorResponse.Failure -> { emitError(r.error); return@launch }
                }
                _state.value = _state.value.copy(done = if (media.status == "ready") "Медиа добавлено в событие"
                    else "Видео загружено. После обработки оно появится в событии автоматически")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emitError(ErrorModel(customMessage = "Не удалось добавить медиа. Повторите попытку")) }
            finally { _state.value = _state.value.copy(loading = false) }
        }
    }
}
