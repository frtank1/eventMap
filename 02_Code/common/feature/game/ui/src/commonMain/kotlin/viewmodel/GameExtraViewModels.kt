package com.eventmap.game.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.platform.geo.GeoFix
import com.eventmap.core.platform.geo.GeoProvider
import com.eventmap.game.ui.data.CapsuleNet
import com.eventmap.game.ui.data.CreateCapsuleBody
import com.eventmap.game.ui.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =====================================================================
//  R2 · Лидерборд игроков  +  R3 · Капсулы времени
// =====================================================================

data class LeaderRowUi(
    val rank: Int,
    val name: String,
    val xp: Int,
    val level: Int,
    val isMe: Boolean,
)

class LeaderboardViewModel(private val repository: GameRepository) : ScreenModel {

    private val _global = MutableStateFlow<List<LeaderRowUi>>(emptyList())
    val global: StateFlow<List<LeaderRowUi>> = _global.asStateFlow()

    private val _friends = MutableStateFlow<List<LeaderRowUi>>(emptyList())
    val friends: StateFlow<List<LeaderRowUi>> = _friends.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** true — данные бэка; false — офлайн-демо (экран показывает sample) */
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _loading.value = true
            val g = repository.userLeaderboard("global")
            val f = repository.userLeaderboard("friends")
            if (g is KtorResponse.Success) {
                _global.value = g.data.map { LeaderRowUi(it.rank, it.name, it.xp.toInt(), it.level, it.isMe) }
                _live.value = true
            }
            if (f is KtorResponse.Success) {
                _friends.value = f.data.map { LeaderRowUi(it.rank, it.name, it.xp.toInt(), it.level, it.isMe) }
            }
            _loading.value = false
        }
    }
}

// ── Капсулы времени ──

sealed interface CapsuleSubmit {
    data object Idle : CapsuleSubmit
    data object Sending : CapsuleSubmit
    data object Done : CapsuleSubmit
    data class Error(val message: String) : CapsuleSubmit
}

class TimeCapsuleViewModel(
    private val repository: GameRepository,
    private val geo: GeoProvider,
) : ScreenModel {

    private val _fix = MutableStateFlow<GeoFix?>(null)
    val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private val _nearby = MutableStateFlow<List<CapsuleNet>>(emptyList())
    val nearby: StateFlow<List<CapsuleNet>> = _nearby.asStateFlow()

    private val _submit = MutableStateFlow<CapsuleSubmit>(CapsuleSubmit.Idle)
    val submit: StateFlow<CapsuleSubmit> = _submit.asStateFlow()

    /** Открытая капсула: показываем послание в шторке/карточке */
    private val _opened = MutableStateFlow<CapsuleNet?>(null)
    val opened: StateFlow<CapsuleNet?> = _opened.asStateFlow()

    init { locateAndLoad() }

    fun locateAndLoad() {
        screenModelScope.launch {
            val f = geo.current()
            _fix.value = f
            if (f != null) {
                (repository.capsulesNearby(f.lat, f.lng) as? KtorResponse.Success)?.let { _nearby.value = it.data }
            }
        }
    }

    fun create(bodyText: String, recipientType: String) {
        if (_submit.value is CapsuleSubmit.Sending) return
        if (bodyText.isBlank()) {
            _submit.value = CapsuleSubmit.Error("Напиши послание — пустую капсулу закапывать жалко")
            return
        }
        screenModelScope.launch {
            _submit.value = CapsuleSubmit.Sending
            val f = _fix.value ?: geo.current().also { _fix.value = it }
            if (f == null) {
                _submit.value = CapsuleSubmit.Error("Нужна геолокация — капсула привязывается к месту")
                return@launch
            }
            val r = repository.createCapsule(
                CreateCapsuleBody(
                    lat = f.lat, lng = f.lng,
                    bodyText = bodyText.trim(),
                    recipientType = recipientType,
                ),
            )
            _submit.value = when (r) {
                is KtorResponse.Success -> CapsuleSubmit.Done
                is KtorResponse.Failure -> CapsuleSubmit.Error("Не удалось закопать — проверь сеть")
            }
        }
    }

    fun open(capsule: CapsuleNet) {
        screenModelScope.launch {
            val f = _fix.value
            when (val r = repository.openCapsule(capsule.id, f?.lat, f?.lng)) {
                is KtorResponse.Success -> {
                    _opened.value = r.data
                    _nearby.value = _nearby.value.map { if (it.id == r.data.id) r.data else it }
                }
                is KtorResponse.Failure ->
                    _submit.value = CapsuleSubmit.Error("Капсула не открылась — подойди ближе к месту")
            }
        }
    }

    fun dismissOpened() { _opened.value = null }

    fun resetError() {
        if (_submit.value is CapsuleSubmit.Error) _submit.value = CapsuleSubmit.Idle
    }
}
