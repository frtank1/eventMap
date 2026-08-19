package com.eventmap.event.ui

import com.eventmap.event.ui.viewmodel.CommentsViewModel
import com.eventmap.event.ui.viewmodel.MyEventsViewModel
import com.eventmap.event.ui.viewmodel.RateEventViewModel
import com.eventmap.event.ui.viewmodel.CreateEventViewModel
import com.eventmap.event.ui.viewmodel.EventDetailViewModel
import org.koin.dsl.module

val eventModule = module {
    factory { EventDetailViewModel(repository = get(), reminders = get()) }
    factory { CreateEventViewModel(repository = get(), drafts = get()) }
    factory { MyEventsViewModel(repository = get()) } // мои события: правка/удаление
    factory { CommentsViewModel(repository = get()) } // D9 → /events/{id}/comments
    factory { RateEventViewModel(repository = get()) } // P4 → /events/{id}/rating
}
