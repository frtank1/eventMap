package com.eventmap.map.screen

import org.koin.dsl.module

val mapModule = module {
    factory { MapViewModel(eventRepository = get()) }
}
