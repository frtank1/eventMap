package com.eventmap.example.data

import com.eventmap.example.ExampleRepository
import org.koin.dsl.module

val exampleRepositoryModule = module {
    single { ExampleRemoteDataSource(httpClient = get(), config = get()) }
    single<ExampleRepository> { ExampleRepositoryImpl(remoteDataSource = get()) }
}
