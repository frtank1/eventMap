package com.eventmap.event.data

import com.eventmap.event.EventRepository
import com.eventmap.event.data.ktor.EventRemoteDataSource
import org.koin.dsl.module

val eventRepositoryModule = module {
    single { EventRemoteDataSource(httpClient = get()) }
    single<EventRepository> { EventRepositoryImpl(remote = get()) }
}
