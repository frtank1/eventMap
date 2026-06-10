package com.eventmap.feed.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.feed.FeedRepository
import com.eventmap.feed.models.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: FeedRepository,
) : BaseScreenModel() {

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            _items.value = (repository.getFeed() as? KtorResponse.Success)?.data ?: sample()
            _loading.value = false
        }
    }

    private fun sample() = listOf(
        FeedItem("f1", "1", "Концерт у Байтерека", "Astana Live", "сегодня · 18:00", "Концерт"),
        FeedItem("f2", "3", "Йога в парке", "City Yoga", "завтра · 08:00", "Йога"),
        FeedItem("f3", "5", "Квиз «Мозгобойня»", "QuizClub", "сегодня · 19:30", "Квиз"),
    )
}
