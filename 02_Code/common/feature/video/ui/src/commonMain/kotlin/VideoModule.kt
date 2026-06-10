package com.eventmap.video.ui

import com.eventmap.video.ui.viewmodel.VideoViewModel
import org.koin.dsl.module

val videoModule = module {
    factory { VideoViewModel(repository = get()) }
}
