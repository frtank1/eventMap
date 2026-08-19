package com.eventmap.applock

import org.koin.dsl.module

val appLockModule = module {
    single {
        AppLockController(
            appLockStorage = get(),
            tokenStorage = get(),
            biometric = get(),
            authRepository = get(),
        )
    }
}
