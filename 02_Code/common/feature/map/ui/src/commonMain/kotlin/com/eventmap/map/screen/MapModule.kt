package com.eventmap.map.screen

import com.eventmap.core.network.CoreConfig
import org.koin.dsl.module

val mapModule = module {
    factory { MapViewModel(eventRepository = get(), demoMode = get<CoreConfig>().demoMode) }
}
