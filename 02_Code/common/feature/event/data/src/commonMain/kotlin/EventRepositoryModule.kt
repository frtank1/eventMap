package com.eventmap.event.data

import com.eventmap.event.EventRepository
import com.eventmap.event.data.ktor.EventRemoteDataSource
import com.eventmap.event.data.ktor.MediaRemoteDataSource
import org.koin.core.qualifier.named
import org.koin.dsl.module

val eventRepositoryModule = module {
    single { EventRemoteDataSource(httpClient = get()) }
    single { MediaRemoteDataSource(apiClient = get(), uploadClient = get(named("upload"))) }
    single<EventRepository> { EventRepositoryImpl(remote = get(), media = get()) }
}
