package com.eventmap.business.ui

import com.eventmap.business.ui.data.businessDataModule
import com.eventmap.business.ui.viewmodel.BusinessMeViewModel
import com.eventmap.business.ui.viewmodel.BusinessRegViewModel
import com.eventmap.business.ui.viewmodel.QuestBuilderViewModel
import org.koin.dsl.module

// M · Бизнес: гейт/статус (/business/me), заявка (/business/apply), конструктор квеста (POST /quests)
val businessModule = module {
    includes(businessDataModule)
    factory { BusinessMeViewModel(repository = get()) }
    factory { BusinessRegViewModel(repository = get()) }
    factory { QuestBuilderViewModel(repository = get(), geo = get()) } // geo — core.platform.geoModule
}
