package com.eventmap.search.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.search.SearchRepository
import com.eventmap.search.models.SearchHit
import com.eventmap.search.models.SearchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: SearchRepository,
) : BaseScreenModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _type = MutableStateFlow(SearchType.Events)
    val type: StateFlow<SearchType> = _type.asStateFlow()

    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun onQueryChange(q: String) {
        _query.value = q
        runSearch()
    }

    fun onTypeChange(t: SearchType) {
        _type.value = t
        runSearch()
    }

    private fun runSearch() {
        val q = _query.value.trim()
        if (q.isBlank()) {
            _results.value = emptyList()
            return
        }
        screenModelScope.launch {
            _loading.value = true
            val type = _type.value
            val data = (repository.search(q, type.api) as? KtorResponse.Success)?.data
                ?: sample(q, type)
            _results.value = data
            _loading.value = false
        }
    }

    // Демо-результаты (без бэка): фильтруем заготовки по запросу.
    private fun sample(q: String, type: SearchType): List<SearchHit> {
        val all = when (type) {
            SearchType.Events -> listOf(
                SearchHit("1", "events", "Концерт у Байтерека", "Сегодня · Концерт", eventId = "1", emoji = "🎵"),
                SearchHit("2", "events", "Кайрат — Астана", "Сегодня · Спорт", eventId = "2", emoji = "⚽"),
                SearchHit("5", "events", "Квиз «Мозгобойня»", "Сегодня · Квиз", eventId = "5", emoji = "🧩"),
            )
            SearchType.Organizers -> listOf(
                SearchHit("o1", "organizers", "Astana Live", "12 событий", emoji = "🎤"),
                SearchHit("o2", "organizers", "City Yoga", "8 событий", emoji = "🧘"),
            )
            SearchType.People -> listOf(
                SearchHit("u1", "people", "Айдана", "@aidana", emoji = "🧑"),
                SearchHit("u2", "people", "Тимур", "@timur", emoji = "🧑"),
            )
            SearchType.Genres -> listOf(
                SearchHit("g1", "genres", "Концерты", "320 событий", emoji = "🎵"),
                SearchHit("g2", "genres", "Спорт", "210 событий", emoji = "⚽"),
            )
        }
        return all.filter { it.title.contains(q, ignoreCase = true) || q.length <= 2 }
    }
}
