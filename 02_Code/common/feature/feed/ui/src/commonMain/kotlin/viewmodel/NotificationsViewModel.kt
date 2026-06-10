package com.eventmap.feed.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.feed.FeedRepository
import com.eventmap.feed.models.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: FeedRepository,
) : BaseScreenModel() {

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            _items.value = (repository.getNotifications() as? KtorResponse.Success)?.data ?: sample()
            _loading.value = false
        }
    }

    private fun sample() = listOf(
        NotificationItem("n1", "live", "🔴 Концерт у Байтерека уже идёт — загляни!", "5 мин назад", false),
        NotificationItem("n2", "subscribe", "Astana Live опубликовал новое событие", "1 ч назад", false),
        NotificationItem("n3", "event", "Напоминание: Квиз «Мозгобойня» сегодня в 19:30", "3 ч назад", true),
    )
}
