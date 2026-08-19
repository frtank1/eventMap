package com.eventmap.game.ui

import com.eventmap.game.ui.data.gameDataModule
import com.eventmap.game.ui.screens.sampleGameState
import com.eventmap.game.ui.viewmodel.GameHubViewModel
import com.eventmap.game.ui.viewmodel.LeaderboardViewModel
import com.eventmap.game.ui.viewmodel.TimeCapsuleViewModel
import org.koin.dsl.module

// DI игрового хаба: сетевой слой (/game/*, /districts/leaderboard, /capsules) + ViewModels R1–R3
val gameModule = module {
    includes(gameDataModule)
    factory { GameHubViewModel(repository = get(), sample = { sampleGameState() }) }
    factory { LeaderboardViewModel(repository = get()) } // R2: топ игроков по XP
    factory { TimeCapsuleViewModel(repository = get(), geo = get()) } // R3: geo — core.platform.geoModule
}
