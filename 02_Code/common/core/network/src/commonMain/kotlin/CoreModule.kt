package com.eventmap.core.network

import com.eventmap.core.network.error.GlobalErrorBus
import com.eventmap.core.network.json.serializationModule
import ktorModule
import org.koin.dsl.module

val coreModule = module {
    includes(
        serializationModule,
        ktorModule,
    )
    single { GlobalErrorBus() }
}