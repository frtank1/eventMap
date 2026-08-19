package com.eventmap.auth.data

import com.eventmap.auth.AuthRepository
import com.eventmap.auth.data.ktor.AuthRemoteDataSource
import org.koin.dsl.module

val authRepositoryModule = module {
    single { AuthRemoteDataSource(httpClient = get()) }
    single<AuthRepository> {
        AuthRepositoryImpl(remote = get(), tokenStorage = get(), appLockStorage = get())
    }
}
