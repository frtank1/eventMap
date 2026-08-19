package com.eventmap.map.screen

import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import com.eventmap.map.EventCategory
import com.eventmap.map.EventState
import com.eventmap.map.LatLng
import com.eventmap.map.MapEvent
import com.eventmap.core.datastore.PulseSettings
import com.eventmap.core.platform.geo.GeoProvider
import com.eventmap.map.data.AnomalyNet
import com.eventmap.map.data.FriendLocationNet
import com.eventmap.map.data.MapPulseRepository
import com.eventmap.map.data.PulseEventNet
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
    private val pulseRepository: MapPulseRepository? = null, // null в старых тестах
    private val geo: GeoProvider? = null,                    // null в старых тестах
    private val pulseSettings: PulseSettings? = null,        // opt-in на пинг (I2)
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

    // Участники выбранного LIVE-события (кружки C6); пусто = ещё грузится/никого
    private val _liveParticipants = MutableStateFlow<List<String>>(emptyList())
    val liveParticipants: StateFlow<List<String>> = _liveParticipants.asStateFlow()

    // ── Режимы C1 (G1–G3): пульс/туман/люди ──────────────────────────
    private val _mode = MutableStateFlow(MapMode.None)
    val mode: StateFlow<MapMode> = _mode.asStateFlow()

    /** glow по eventId (G1) — GET /map/pulse */
    private val _pulse = MutableStateFlow<Map<String, PulseEventNet>>(emptyMap())
    val pulse: StateFlow<Map<String, PulseEventNet>> = _pulse.asStateFlow()

    /** спонтанные сгустки людей «⚡ здесь что-то происходит» (G9) */
    private val _anomalies = MutableStateFlow<List<AnomalyNet>>(emptyList())
    val anomalies: StateFlow<List<AnomalyNet>> = _anomalies.asStateFlow()

    /** туман войны (G3): открытые районы; null — ещё не загружено */
    private val _fogVisited = MutableStateFlow<Int?>(null)
    val fogVisited: StateFlow<Int?> = _fogVisited.asStateFlow()

    /** друзья, делящиеся геолокацией (C10/L5) — пины режима «Люди» */
    private val _friends = MutableStateFlow<List<FriendLocationNet>>(emptyList())
    val friends: StateFlow<List<FriendLocationNet>> = _friends.asStateFlow()

    /** квест-компас G6: ближайшее событие «по вкусу» (учитывает выбранные категории) */
    data class CompassHint(val event: MapEvent, val distanceM: Int?)
    private val _compass = MutableStateFlow<CompassHint?>(null)
    val compass: StateFlow<CompassHint?> = _compass.asStateFlow()

    init {
        reload()
        startPingLoop()
    }

    // Гео-пинг «Пульса города» (I2, §8.5): ТОЛЬКО при включённом тумблере Sharing.
    // Живёт, пока жива карта (scope отменяется в onDispose). Тумблер перечитывается
    // каждый цикл — выключение в настройках останавливает пинги без рестарта.
    private fun startPingLoop() {
        val repo = pulseRepository ?: return
        val geoProvider = geo ?: return
        scope.launch {
            var wasSharing = false
            while (true) {
                val sharing = pulseSettings?.isSharingEnabled() == true
                if (sharing) {
                    geoProvider.current()?.let { fix ->
                        repo.ping(fix.lat, fix.lng)          // аноним: пульс города (G1/G9)
                        repo.shareLocation(fix.lat, fix.lng) // именной: друзьям (C10/L5)
                    }
                    wasSharing = true
                } else if (wasSharing) {
                    // тумблер выключили — убираем позицию; при сетевой ошибке ретраим следующим тиком
                    if (repo.stopSharing() is KtorResponse.Success) wasSharing = false
                }
                // режим «Люди» открыт — освежаем позиции друзей (двигаются/перестают делиться)
                if (_mode.value == MapMode.People) loadFriends()
                kotlinx.coroutines.delay(PING_INTERVAL_MS)
            }
        }
    }

    fun setMode(mode: MapMode) {
        _mode.value = if (_mode.value == mode) MapMode.None else mode
        when (_mode.value) {
            MapMode.Pulse, MapMode.Heat, MapMode.Flow -> loadPulse() // тепло/поток — из glow, crowd10m и аномалий
            MapMode.People -> { loadPulse(); loadFriends() }
            MapMode.Fog -> loadFog()
            MapMode.None -> Unit
        }
    }

    private fun loadPulse() {
        val repo = pulseRepository ?: return
        scope.launch {
            when (val r = repo.pulse()) {
                is KtorResponse.Success -> {
                    _pulse.value = r.data.events.associateBy { it.eventId }
                    _anomalies.value = r.data.anomalies
                }
                is KtorResponse.Failure -> if (demoMode) {
                    // оффлайн-демо G9: одна аномалия у Байтерека
                    _anomalies.value = listOf(AnomalyNet(id = "demo", lat = 51.1283, lng = 71.4306, userCount = 34))
                }
            }
        }
    }

    // C10: позиции друзей (бэк отдаёт только подтверждённых друзей и свежие фиксы)
    private fun loadFriends() {
        val repo = pulseRepository ?: return
        scope.launch {
            when (val r = repo.friendLocations()) {
                is KtorResponse.Success -> _friends.value = r.data
                is KtorResponse.Failure -> if (demoMode) {
                    _friends.value = listOf(
                        FriendLocationNet("demo-1", "Аян", 51.1300, 71.4280, "2 мин назад"),
                        FriendLocationNet("demo-2", "Дана", 51.1240, 71.4380, "5 мин назад"),
                    )
                }
            }
        }
    }

    /** «Позвать с собой» (C10): пуш другу; onDone(true) — успех */
    fun waveToFriend(userId: String, onDone: (Boolean) -> Unit = {}) {
        val repo = pulseRepository ?: return onDone(false)
        scope.launch {
            onDone(repo.wave(userId) is KtorResponse.Success)
        }
    }

    private fun loadFog() {
        val repo = pulseRepository ?: return
        scope.launch {
            when (val r = repo.fog()) {
                is KtorResponse.Success -> _fogVisited.value = r.data.visitedDistrictIds.size
                is KtorResponse.Failure -> if (demoMode) _fogVisited.value = 3
            }
        }
    }

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
            refreshCompass()
        }
    }

    // G6 «движ по вкусу»: ближайшее событие выбранных категорий (или любое LIVE/скоро).
    // Гео берём одноразово; нет разрешения — компас без расстояния (первое LIVE).
    private suspend fun refreshCompass() {
        val candidates = applyFilter(_filter.value).filter { it.state != EventState.Ending }
        if (candidates.isEmpty()) { _compass.value = null; return }
        val fix = geo?.current()
        _compass.value = if (fix == null) {
            val target = candidates.firstOrNull { it.state == EventState.Live } ?: candidates.first()
            CompassHint(target, distanceM = null)
        } else {
            val target = candidates.minByOrNull { haversineM(fix.lat, fix.lng, it.location.lat, it.location.lon) }!!
            CompassHint(target, haversineM(fix.lat, fix.lng, target.location.lat, target.location.lon).toInt())
        }
    }

    override fun onDispose() {
        scope.cancel()
    }

    fun select(event: MapEvent) {
        _selected.value = event
        // C6: живые кружки участников LIVE-шторки — реальные имена с бэка
        _liveParticipants.value = emptyList()
        if (event.state == EventState.Live || event.state == EventState.Ending) {
            scope.launch {
                _liveParticipants.value =
                    (eventRepository.getParticipants(event.id, "going") as? KtorResponse.Success)
                        ?.data?.map { it.name }.orEmpty()
            }
        }
    }
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
    kind = kind,
    confirmationCount = confirmationCount,
    interestCount = interestCount,
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

// пинг раз в минуту — сервер агрегирует каждые 30 с, чаще не нужно (TTL 15 мин)
private const val PING_INTERVAL_MS = 60_000L

// расстояние между точками в метрах (для компаса G6)
private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = kotlin.math.PI / 180 * (lat2 - lat1)
    val dLng = kotlin.math.PI / 180 * (lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(kotlin.math.PI / 180 * lat1) * kotlin.math.cos(kotlin.math.PI / 180 * lat2) *
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
}

private fun statusToState(s: String): EventState = when (s) {
    "live" -> EventState.Live
    "ended" -> EventState.Ending
    else -> EventState.Soon
}
