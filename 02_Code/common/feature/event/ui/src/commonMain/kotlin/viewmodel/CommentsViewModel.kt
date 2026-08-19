package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// D9 · Комментарии события: GET/POST /events/{id}/comments, POST /comments/{id}/like.
// При недоступном бэке экран остаётся на демо-комментариях (live=false).
class CommentsViewModel(
    private val repository: EventRepository,
) : ScreenModel {

    private val _comments = MutableStateFlow<List<EventComment>>(emptyList())
    val comments: StateFlow<List<EventComment>> = _comments.asStateFlow()

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var eventId: String = ""

    fun load(eventId: String) {
        this.eventId = eventId
        screenModelScope.launch {
            when (val r = repository.getComments(eventId)) {
                is KtorResponse.Success -> {
                    _live.value = true
                    _comments.value = r.data
                }
                is KtorResponse.Failure -> _live.value = false
            }
        }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty() || eventId.isEmpty() || _sending.value) return
        screenModelScope.launch {
            _sending.value = true
            when (val r = repository.addComment(eventId, body)) {
                is KtorResponse.Success -> _comments.value = listOf(r.data) + _comments.value
                is KtorResponse.Failure -> Unit // тихо: сеть могла отвалиться, комментарий не теряем из поля
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
                is KtorResponse.Failure -> Unit
            }
        }
    }
}
