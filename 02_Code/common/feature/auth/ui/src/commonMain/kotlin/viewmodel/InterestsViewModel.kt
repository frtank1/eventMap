package com.eventmap.auth.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.auth.AuthRepository
import com.eventmap.auth.models.Interest
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InterestsViewModel(
    private val repository: AuthRepository,
) : BaseScreenModel() {

    // Каталог жанров (захардкожен под MVP; в проде — с бэка).
    val catalog: List<Interest> = listOf(
        Interest("concert", "Концерты", "🎵"),
        Interest("sport", "Спорт", "⚽"),
        Interest("yoga", "Йога", "🧘"),
        Interest("food", "Еда", "🍔"),
        Interest("kids", "Детям", "🧸"),
        Interest("education", "Лекции", "📚"),
        Interest("quiz", "Квизы", "🧩"),
        Interest("art", "Искусство", "🎨"),
        Interest("party", "Вечеринки", "🎉"),
        Interest("market", "Маркеты", "🛍️"),
        Interest("cinema", "Кино", "🎬"),
        Interest("book", "Книжклубы", "📖"),
    )

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun toggle(id: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun submit() {
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.saveInterests(_selected.value.toList())) {
                is KtorResponse.Success -> {
                    _loading.value = false
                    _done.value = true
                }
                is KtorResponse.Failure -> {
                    _loading.value = false
                    _done.value = true // TODO(dev): без бэка всё равно пускаем дальше
                    emitError(r.error)
                }
            }
        }
    }

    fun skip() {
        _done.value = true
    }
}
