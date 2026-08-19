package com.eventmap.quest.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.platform.geo.GeoFix
import com.eventmap.core.platform.geo.GeoProvider
import com.eventmap.quest.ui.data.QuestCheckinBody
import com.eventmap.quest.ui.data.QuestRepository
import com.eventmap.quest.ui.data.toUi
import com.eventmap.quest.ui.models.CheckInType
import com.eventmap.quest.ui.models.QuestItem
import com.eventmap.quest.ui.models.QuestPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
//  Q · ViewModels: каталог Q1, карточка Q2, чек-ин Q3/Q4
//  Паттерн проекта: API → при ошибке остаёмся на sample-данных (см. MapViewModel)
// =====================================================================

class QuestCatalogViewModel(
    private val repository: QuestRepository,
    private val sample: () -> List<QuestItem>,
) : ScreenModel {

    private val _items = MutableStateFlow<List<QuestItem>>(emptyList())
    val items: StateFlow<List<QuestItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** true — показываем данные бэка; false — оффлайн-демо */
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.nearby()) {
                is KtorResponse.Success -> {
                    val fromApi = r.data.map { it.toUi() }
                    _live.value = true
                    // пустой бэк не должен оставлять экран мёртвым — показываем демо
                    _items.value = fromApi.ifEmpty { sample() }
                }
                is KtorResponse.Failure -> {
                    _live.value = false
                    _items.value = sample()
                }
            }
            _loading.value = false
        }
    }
}

class QuestDetailViewModel(
    private val repository: QuestRepository,
) : ScreenModel {

    private val _quest = MutableStateFlow<QuestItem?>(null)
    val quest: StateFlow<QuestItem?> = _quest.asStateFlow()

    /** initial — квест из каталога (мгновенный рендер), затем освежаем с бэка */
    fun load(initial: QuestItem) {
        if (_quest.value == null) _quest.value = initial
        screenModelScope.launch {
            when (val r = repository.byId(initial.id)) {
                is KtorResponse.Success -> _quest.value = r.data.toUi()
                is KtorResponse.Failure -> Unit // остаёмся на переданном
            }
        }
    }
}

class QuestCheckInViewModel(
    private val repository: QuestRepository,
    private val geo: GeoProvider,
) : ScreenModel {

    sealed interface State {
        data object Idle : State
        /** stage — что сейчас происходит: «Определяем позицию…» / «Загружаем фото…» / «Проверяем…» */
        data class Loading(val stage: String) : State
        data class Success(val finished: Boolean, val xpGained: Int, val prizeQr: String?) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Гео- и QR-чекин. Гео берём с устройства (GeoProvider); QR геолокации не требует (§8.3). */
    fun checkin(point: QuestPoint, qrPayload: String? = null) {
        if (demoPass(point)) return
        screenModelScope.launch {
            val fix = if (point.checkInType == CheckInType.Qr) null else locateOrFail() ?: return@launch
            doCheckin(point, fix, qrPayload = qrPayload, photoUrl = null)
        }
    }

    /** Фото-чекин Q3: снимок из галереи → media/presign → PUT → confirm → чек-ин с mediaUrl. */
    fun checkinWithPhoto(point: QuestPoint, bytes: ByteArray, contentType: String) {
        if (demoPass(point)) return
        screenModelScope.launch {
            val fix = locateOrFail() ?: return@launch
            _state.value = State.Loading("Загружаем фото…")
            val url = when (val up = repository.uploadPhoto(bytes, contentType)) {
                is KtorResponse.Success -> up.data
                is KtorResponse.Failure -> {
                    _state.value = State.Error("Не удалось загрузить фото — проверь сеть и попробуй ещё раз")
                    return@launch
                }
            }
            doCheckin(point, fix, qrPayload = null, photoUrl = url)
        }
    }

    fun reset() { _state.value = State.Idle }

    // демо-квест без бэка (sample) — локальный успех, как в прототипе
    private fun demoPass(point: QuestPoint): Boolean {
        if (point.questId.isNotEmpty()) return false
        _state.value = State.Success(finished = false, xpGained = 15, prizeQr = null)
        return true
    }

    /** Позиция устройства; null — уже показали ошибку. */
    private suspend fun locateOrFail(): GeoFix? {
        _state.value = State.Loading("Определяем позицию…")
        val fix = geo.current()
        if (fix == null) {
            _state.value = State.Error("Не видим твою геолокацию. Разреши доступ к гео в настройках и включи GPS")
        }
        return fix
    }

    private suspend fun doCheckin(point: QuestPoint, fix: GeoFix?, qrPayload: String?, photoUrl: String?) {
        _state.value = State.Loading("Проверяем…")
        val body = QuestCheckinBody(
            lat = fix?.lat,
            lng = fix?.lng,
            mediaUrl = photoUrl,
            qrPayload = qrPayload,
        )
        _state.value = when (val r = repository.checkin(point.questId, point.id, body)) {
            is KtorResponse.Success -> State.Success(r.data.finished, r.data.xpGained, r.data.prizeQr)
            is KtorResponse.Failure ->
                State.Error(r.error.error?.message ?: r.error.message ?: "Не получилось отметиться — попробуй ещё раз")
        }
    }
}
