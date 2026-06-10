package com.eventmap.video.data

import com.eventmap.video.VideoRepository
import com.eventmap.video.data.ktor.VideoRemoteDataSource
import org.koin.dsl.module

val videoRepositoryModule = module {
    single { VideoRemoteDataSource(httpClient = get()) }
    single<VideoRepository> { VideoRepositoryImpl(remote = get()) }
}
