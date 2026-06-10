package com.eventmap.map.screen

import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import com.eventmap.map.EventCategory
import com.eventmap.map.EventState
import com.eventmap.map.LatLng
import com.eventmap.map.MapEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние домашней карты. Тянет события из EventRepository (GET /events).
 * В demoMode при недоступном бэкенде остаётся на sampleEvents (карта не пустая);
 * в боевом режиме ошибка уходит в errorMessage (снек экрана, см. ERRORS.md).
 * Фильтрация локальная.
 */
class MapViewModel(
    private val eventRepository: EventRepository,
    private val demoMode: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default, // в тестах — TestDispatcher
) : BaseScreenModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var all: List<MapEvent> = if (demoMode) sampleEvents() else emptyList()

    private val _filter = MutableStateFlow(MapFilter())
    val filter: StateFlow<MapFilter> = _filter.asStateFlow()

    private val _events = MutableStateFlow(applyFilter(MapFilter()))
    val events: StateFlow<List<MapEvent>> = _events.asStateFlow()

    private val _selected = MutableStateFlow<MapEvent?>(null)
    val selected: StateFlow<MapEvent?> = _selected.asStateFlow()

    init { reload() }

    fun reload() {
        scope.launch {
            when (val r = eventRepository.getEvents()) {
                is KtorResponse.Success -> {
                    // в demoMode пустой ответ не затирает sample-данные
                    if (r.data.isNotEmpty() || !demoMode) {
                        all = r.data.map { it.toMapEvent() }
                        _events.value = applyFilter(_filter.value)
                    }
                }
                is KtorResponse.Failure ->
                    if (!demoMode) emitError(r.error) // в demoMode остаёмся на sampleEvents
            }
        }
    }

    override fun onDispose() {
        scope.cancel()
    }

    fun select(event: MapEvent) { _selected.value = event }
    fun clearSelection() { _selected.value = null }

    fun setWhen(w: WhenFilter) = update(_filter.value.copy(whenFilter = w))
    fun toggleCategory(c: EventCategory) {
        val cur = _filter.value.categories.toMutableSet()
        if (!cur.add(c)) cur.remove(c)
        update(_filter.value.copy(categories = cur))
    }
    fun setFreeOnly(value: Boolean) = update(_filter.value.copy(freeOnly = value))
    fun setRadius(km: Float) = update(_filter.value.copy(radiusKm = km))
    fun toggleSound() = update(_filter.value.copy(soundOn = !_filter.value.soundOn))
    fun toggleLayer(layer: MapLayer) {
        val cur = _filter.value.layers.toMutableSet()
        if (!cur.add(layer)) cur.remove(layer)
        update(_filter.value.copy(layers = cur))
    }
    fun resetFilter() = update(MapFilter())

    private fun update(newFilter: MapFilter) {
        _filter.value = newFilter
        _events.value = applyFilter(newFilter)
    }

    private fun applyFilter(f: MapFilter): List<MapEvent> = all.filter { e ->
        val byWhen = when (f.whenFilter) {
            WhenFilter.Now -> e.state == EventState.Live
            else -> true
        }
        val byCategory = f.categories.isEmpty() || e.category in f.categories
        val byPrice = !f.freeOnly || e.isFree
        val byRadius = e.distanceKm <= f.radiusKm + 0.001
        byWhen && byCategory && byPrice && byRadius
    }
}

// EventDetail (бэкенд) → MapEvent (карта)
private fun EventDetail.toMapEvent(): MapEvent = MapEvent(
    id = id,
    location = LatLng(location.lat, location.lng),
    category = categoryToEnum(category),
    state = statusToState(status),
    title = title,
    subtitle = description.ifBlank { category },
    timeLabel = if (status == "live") "Идёт сейчас" else "Скоро",
    priceLabel = if (price.isFree) "Бесплатно" else "${price.amount} ${price.currency}",
    distanceKm = 0.0,
    isFree = price.isFree,
    watching = liveViewers,
)

private fun categoryToEnum(c: String): EventCategory = when (c) {
    "Концерт" -> EventCategory.Concert
    "Спорт" -> EventCategory.Stadium
    "Йога" -> EventCategory.Yoga
    "Еда" -> EventCategory.FoodDiscount
    "Скидки" -> EventCategory.ShopDiscount
    "Детям" -> EventCategory.KidsEvent
    "Лекция" -> EventCategory.OpenLesson
    "Квиз" -> EventCategory.Quiz
    "Книжклуб" -> EventCategory.BookClub
    else -> EventCategory.Concert
}

private fun statusToState(s: String): EventState = when (s) {
    "live" -> EventState.Live
    "ended" -> EventState.Ending
    else -> EventState.Soon
}
