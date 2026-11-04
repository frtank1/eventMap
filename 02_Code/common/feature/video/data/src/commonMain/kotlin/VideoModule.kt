package com.eventmap.video.data

import com.eventmap.video.VideoRepository
import com.eventmap.video.data.ktor.VideoRemoteDataSource
import org.koin.dsl.module
import org.koin.core.qualifier.named

val videoRepositoryModule = module {
    single { VideoRemoteDataSource(httpClient = get(), uploadClient = get(named("upload"))) }
    single<VideoRepository> { VideoRepositoryImpl(remote = get()) }
}
