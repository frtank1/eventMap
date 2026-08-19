package com.eventmap.game.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.game.ui.data.GameRepository
import com.eventmap.game.ui.data.buildGameState
import com.eventmap.game.ui.models.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// R1 · Игровой хаб: XP/уровень/значки — /game/profile, миссии — /game/missions,
// лидерборд районов — /districts/leaderboard. При недоступном бэке — демо-состояние.
class GameHubViewModel(
    private val repository: GameRepository,
    private val sample: () -> GameState,
) : ScreenModel {

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            val base = sample()
            val profile = repository.profile()
            if (profile !is KtorResponse.Success) {
                _state.value = base // оффлайн-демо
                return@launch
            }
            val missions = (repository.missions() as? KtorResponse.Success)?.data.orEmpty()
            val leaderboard = (repository.leaderboard() as? KtorResponse.Success)?.data.orEmpty()
            _state.value = buildGameState(profile.data, missions, leaderboard, base)
        }
    }
}
