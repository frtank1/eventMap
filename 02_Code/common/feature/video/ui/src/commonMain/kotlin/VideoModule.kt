package com.eventmap.video.ui

import com.eventmap.video.ui.viewmodel.RecordVideoViewModel
import com.eventmap.video.ui.viewmodel.VideoViewModel
import org.koin.dsl.module

val videoModule = module {
    factory { VideoViewModel(repository = get()) }
    // раньше не был зарегистрирован — RecordVideoScreen падал бы на koinScreenModel
    factory { RecordVideoViewModel(repository = get()) }
}
