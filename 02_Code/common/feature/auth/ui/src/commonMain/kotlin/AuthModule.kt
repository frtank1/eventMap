package com.eventmap.auth.ui

import com.eventmap.auth.ui.viewmodel.ForgotViewModel
import com.eventmap.auth.ui.viewmodel.InterestsViewModel
import com.eventmap.auth.ui.viewmodel.LoginViewModel
import com.eventmap.auth.ui.viewmodel.RegisterViewModel
import org.koin.dsl.module

val authModule = module {
    factory { LoginViewModel(repository = get()) }
    factory { RegisterViewModel(repository = get()) }
    factory { ForgotViewModel(repository = get()) }
    factory { InterestsViewModel(repository = get()) }
}
