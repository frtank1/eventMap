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
import kotlinx.coroutines.Job

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
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _query.value = q
        runSearch()
    }

    fun onTypeChange(t: SearchType) {
        _type.value = t
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        clearError()
        val q = _query.value.trim()
        _results.value = emptyList()
        if (q.isBlank()) {
            _loading.value = false
            return
        }
        searchJob = screenModelScope.launch {
            _loading.value = true
            val type = _type.value
            when (val result = repository.search(q, type.api)) {
                is KtorResponse.Success -> _results.value = result.data
                is KtorResponse.Failure -> emitError(result.error)
            }
            _loading.value = false
        }
    }

}
