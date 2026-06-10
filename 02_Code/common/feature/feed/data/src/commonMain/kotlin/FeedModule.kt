package com.eventmap.feed.data

import com.eventmap.feed.FeedRepository
import com.eventmap.feed.data.ktor.FeedRemoteDataSource
import org.koin.dsl.module

val feedRepositoryModule = module {
    single { FeedRemoteDataSource(httpClient = get()) }
    single<FeedRepository> { FeedRepositoryImpl(remote = get()) }
}
