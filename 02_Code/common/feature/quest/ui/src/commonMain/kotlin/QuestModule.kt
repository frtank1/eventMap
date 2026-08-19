package com.eventmap.quest.ui

import com.eventmap.quest.ui.data.questDataModule
import com.eventmap.quest.ui.screens.sampleQuests
import com.eventmap.quest.ui.viewmodel.QuestCatalogViewModel
import com.eventmap.quest.ui.viewmodel.QuestCheckInViewModel
import com.eventmap.quest.ui.viewmodel.QuestDetailViewModel
import org.koin.dsl.module

// DI квестов: сетевой слой (/quests/*) + ViewModels экранов Q1–Q4
val questModule = module {
    includes(questDataModule)
    factory { QuestCatalogViewModel(repository = get(), sample = { sampleQuests() }) }
    factory { QuestDetailViewModel(repository = get()) }
    factory { QuestCheckInViewModel(repository = get(), geo = get()) } // geo — core.platform.geoModule
}
