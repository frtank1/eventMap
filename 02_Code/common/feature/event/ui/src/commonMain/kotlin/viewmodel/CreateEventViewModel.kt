package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.EventRepository
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateForm(
    val title: String = "",
    val description: String = "",
    val locationName: String = "",
    val date: String = "",
    val time: String = "",
    val category: String = "",
    val isPaid: Boolean = false,
    val priceAmount: String = "",
    val limit: String = "",
)

class CreateEventViewModel(
    private val repository: EventRepository,
) : BaseScreenModel() {

    private val _step = MutableStateFlow(0)         // 0..2
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _form = MutableStateFlow(CreateForm())
    val form: StateFlow<CreateForm> = _form.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _created = MutableStateFlow(false)
    val created: StateFlow<Boolean> = _created.asStateFlow()

    fun update(transform: (CreateForm) -> CreateForm) { _form.value = transform(_form.value) }

    fun next() {
        val f = _form.value
        when (_step.value) {
            0 -> if (f.title.isBlank()) return emitError(ErrorModel(customMessage = "Введите название"))
                 else _step.value = 1
            1 -> if (f.locationName.isBlank()) return emitError(ErrorModel(customMessage = "Укажите локацию"))
                 else _step.value = 2
            else -> Unit
        }
    }

    fun back() { if (_step.value > 0) _step.value -= 1 }

    fun publish() {
        val f = _form.value
        if (f.date.isBlank() || f.category.isBlank()) {
            emitError(ErrorModel(customMessage = "Заполните дату и категорию"))
            return
        }
        val draft = CreateEventDraft(
            title = f.title.trim(),
            description = f.description.trim(),
            location = EventLocation(name = f.locationName.trim()),
            startAt = "${f.date}T${f.time.ifBlank { "00:00" }}:00Z",
            endAt = "",
            category = f.category,
            price = if (f.isPaid) EventPrice("paid", f.priceAmount.toIntOrNull() ?: 0, "KZT") else EventPrice("free"),
            limit = f.limit.toIntOrNull(),
        )
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.createEvent(draft)) {
                is KtorResponse.Success -> { _loading.value = false; _created.value = true }
                is KtorResponse.Failure -> {
                    _loading.value = false
                    // без бэка — считаем успешно созданным локально
                    _created.value = true
                    emitError(r.error)
                }
            }
        }
    }
}
