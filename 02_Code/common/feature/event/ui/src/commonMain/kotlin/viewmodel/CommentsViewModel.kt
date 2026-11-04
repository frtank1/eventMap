package com.eventmap.event.ui.viewmodel

import com.eventmap.base_screen.BaseScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// D9 · Комментарии события: GET/POST /events/{id}/comments, POST /comments/{id}/like.
// Ошибки видны в UI; неотправленный текст остаётся в поле ввода.
class CommentsViewModel(
    private val repository: EventRepository,
) : BaseScreenModel() {

    private val _comments = MutableStateFlow<List<EventComment>>(emptyList())
    val comments: StateFlow<List<EventComment>> = _comments.asStateFlow()

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var eventId: String = ""

    fun load(eventId: String) {
        this.eventId = eventId
        _comments.value = emptyList()
        _live.value = false
        clearError()
        screenModelScope.launch {
            val r = repository.getComments(eventId)
            if (this@CommentsViewModel.eventId != eventId) return@launch
            when (r) {
                is KtorResponse.Success -> {
                    _live.value = true
                    _comments.value = r.data
                }
                is KtorResponse.Failure -> { _live.value = false; emitError(r.error) }
            }
        }
    }

    fun send(text: String, onSent: () -> Unit = {}) {
        val body = text.trim()
        if (body.isEmpty() || eventId.isEmpty() || _sending.value) return
        val targetEventId = eventId
        _sending.value = true
        screenModelScope.launch {
            when (val r = repository.addComment(targetEventId, body)) {
                is KtorResponse.Success -> if (eventId == targetEventId) {
                    _comments.value = listOf(r.data) + _comments.value
                    clearError()
                    onSent()
                }
                is KtorResponse.Failure -> emitError(r.error)
            }
            _sending.value = false
        }
    }

    fun toggleLike(commentId: String) {
        screenModelScope.launch {
            when (val r = repository.toggleCommentLike(commentId)) {
                is KtorResponse.Success -> _comments.value = _comments.value.map {
                    if (it.id == commentId) it.copy(likeCount = r.data, likedByMe = !it.likedByMe) else it
                }
                is KtorResponse.Failure -> emitError(r.error)
            }
        }
    }
}
