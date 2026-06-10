package com.eventmap.feed.ui

import com.eventmap.feed.ui.viewmodel.FeedViewModel
import com.eventmap.feed.ui.viewmodel.NotificationsViewModel
import com.eventmap.feed.ui.viewmodel.SubscriptionsViewModel
import org.koin.dsl.module

val feedModule = module {
    factory { FeedViewModel(repository = get()) }
    factory { NotificationsViewModel(repository = get()) }
    factory { SubscriptionsViewModel(repository = get()) }
}
