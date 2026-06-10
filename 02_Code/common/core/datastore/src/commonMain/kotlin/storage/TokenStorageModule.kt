package com.eventmap.core.datastore

import org.koin.core.qualifier.named
import org.koin.dsl.module

val tokenStorageModule = module {
    single {
        TokenStorage(
            storage = get(named("vault"))
        )
    }
}
