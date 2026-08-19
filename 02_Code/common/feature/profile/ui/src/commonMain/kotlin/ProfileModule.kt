package com.eventmap.profile.ui

import com.eventmap.profile.ui.viewmodel.EditProfileViewModel
import com.eventmap.profile.ui.viewmodel.ProfileViewModel
import com.eventmap.profile.ui.viewmodel.SettingsViewModel
import org.koin.dsl.module

val profileModule = module {
    factory { ProfileViewModel(repository = get()) }
    factory { EditProfileViewModel(repository = get()) }
    factory { SettingsViewModel(pulseSettings = get(), httpClient = get()) } // тумблер I2 + мгновенный stop шеринга
}
