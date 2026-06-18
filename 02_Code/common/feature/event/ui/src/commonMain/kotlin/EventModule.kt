package com.eventmap.event.ui

import com.eventmap.event.ui.viewmodel.CreateEventViewModel
import com.eventmap.event.ui.viewmodel.EventDetailViewModel
import org.koin.dsl.module

val eventModule = module {
    factory { EventDetailViewModel(repository = get()) }
    factory { CreateEventViewModel(repository = get()) }
}
