package com.eventmap.video.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.VideoRepository
import com.eventmap.event.EventRepository
import com.eventmap.video.models.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoViewModel(
    private val repository: VideoRepository,
    private val eventRepository: EventRepository,
) : BaseScreenModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _commentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val commentCounts = _commentCounts.asStateFlow()
    private val counting = mutableSetOf<String>()

    fun loadCommentCount(eventId: String) {
        if (eventId.isBlank() || !counting.add(eventId)) return
        screenModelScope.launch {
            try {
                val result = eventRepository.getEvent(eventId)
                if (result is KtorResponse.Success) _commentCounts.value = _commentCounts.value + (eventId to result.data.commentsCount)
            } finally { counting.remove(eventId) }
        }
    }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            clearError()
            when (val result = repository.now()) {
                is KtorResponse.Success -> _items.value = result.data
                is KtorResponse.Failure -> emitError(result.error)
            }
            _loading.value = false
        }
    }

    fun toggleLike(id: String) {
        if (!reacting.add(id)) return
        screenModelScope.launch {
            try {
                when (val result = repository.react(id)) {
                    is KtorResponse.Success -> _items.value = _items.value.map {
                        if (it.id == id) it.copy(liked = !it.liked, likes = (it.likes + if (it.liked) -1 else 1).coerceAtLeast(0)) else it
                    }
                    is KtorResponse.Failure -> emitError(result.error)
                }
            } finally { reacting.remove(id) }
        }
    }

    private val reacting = mutableSetOf<String>()

}
