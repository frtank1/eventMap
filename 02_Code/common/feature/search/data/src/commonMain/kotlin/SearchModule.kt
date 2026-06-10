package com.eventmap.search.data

import com.eventmap.search.SearchRepository
import com.eventmap.search.data.ktor.SearchRemoteDataSource
import org.koin.dsl.module

val searchRepositoryModule = module {
    single { SearchRemoteDataSource(httpClient = get()) }
    single<SearchRepository> { SearchRepositoryImpl(remote = get()) }
}
