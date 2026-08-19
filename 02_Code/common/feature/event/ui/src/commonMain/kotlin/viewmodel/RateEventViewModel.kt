package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// P4 · Оценка события: звёзды + тег-чипсы + отзыв → POST /events/{id}/rating {value, review, tags}
class RateEventViewModel(
    private val repository: EventRepository,
) : BaseScreenModel() {

    data class State(
        val stars: Int = 0,
        val selectedTags: Set<String> = emptySet(),
        val review: String = "",
        val sending: Boolean = false,
        val sent: Boolean = false,
        val errorText: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setStars(value: Int) {
        _state.value = _state.value.copy(stars = value.coerceIn(1, 5), errorText = null)
    }

    fun toggleTag(tag: String) {
        val cur = _state.value.selectedTags.toMutableSet()
        if (!cur.add(tag)) cur.remove(tag)
        _state.value = _state.value.copy(selectedTags = cur)
    }

    fun setReview(text: String) {
        _state.value = _state.value.copy(review = text.take(2000))
    }

    fun submit(eventId: String) {
        val s = _state.value
        if (s.sending || s.sent) return
        if (s.stars == 0) {
            _state.value = s.copy(errorText = "Поставь хотя бы одну звезду")
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(sending = true, errorText = null)
            when (val r = repository.rateEvent(eventId, s.stars, s.review.trim(), s.selectedTags.toList())) {
                is KtorResponse.Success ->
                    _state.value = _state.value.copy(sending = false, sent = true)
                is KtorResponse.Failure ->
                    _state.value = _state.value.copy(
                        sending = false,
                        errorText = r.error.error?.message ?: r.error.message ?: "Не удалось отправить — попробуй ещё раз",
                    )
            }
        }
    }
}
