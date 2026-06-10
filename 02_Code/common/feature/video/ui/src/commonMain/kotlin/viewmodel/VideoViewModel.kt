package com.eventmap.video.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.video.VideoRepository
import com.eventmap.video.models.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoViewModel(
    private val repository: VideoRepository,
) : BaseScreenModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            _items.value = (repository.now() as? KtorResponse.Success)?.data ?: sample()
            _loading.value = false
        }
    }

    fun toggleLike(id: String) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) else it
        }
        screenModelScope.launch { repository.react(id) }
    }

    private fun sample() = listOf(
        VideoItem("v1", "1", "Концерт у Байтерека", "Айдана", "Лучший вечер 🔥", viewers = 128, likes = 342, category = "Концерт"),
        VideoItem("v2", "2", "Кайрат — Астана", "Тимур", "ГООООЛ!! ⚽", viewers = 540, likes = 1203, category = "Спорт"),
        VideoItem("v3", "5", "Квиз «Мозгобойня»", "Мария", "Мы выиграли 🧩", viewers = 64, likes = 88, category = "Квиз"),
    )
}
