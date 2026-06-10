package com.eventmap.profile.data

import com.eventmap.profile.ProfileRepository
import com.eventmap.profile.data.ktor.ProfileRemoteDataSource
import org.koin.dsl.module

val profileRepositoryModule = module {
    single { ProfileRemoteDataSource(httpClient = get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(remote = get(), tokenStorage = get()) }
}
