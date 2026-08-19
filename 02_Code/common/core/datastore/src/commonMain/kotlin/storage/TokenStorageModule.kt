package com.eventmap.core.datastore

import org.koin.core.qualifier.named
import org.koin.dsl.module

val tokenStorageModule = module {
    single {
        TokenStorage(
            storage = get(named("vault"))
        )
    }
    single {
        AppLockStorage(
            storage = get(named("vault"))
        )
    }
    single {
        // тумблер «Пульс города» (I2): opt-in на гео-пинг карты
        PulseSettings(
            storage = get(named("vault"))
        )
    }
    single {
        // черновик формы создания события (D6)
        EventDraftStorage(
            storage = get(named("vault"))
        )
    }
}
