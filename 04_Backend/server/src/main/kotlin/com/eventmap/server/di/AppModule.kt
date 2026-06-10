package com.eventmap.server.di

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.repository.EventRepositoryImpl
import com.eventmap.server.data.repository.OrganizerRepositoryImpl
import com.eventmap.server.data.repository.RefreshTokenRepositoryImpl
import com.eventmap.server.data.repository.UserRepositoryImpl
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.OrganizerRepository
import com.eventmap.server.domain.repository.RefreshTokenRepository
import com.eventmap.server.domain.repository.UserRepository
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.security.JwtService
import org.koin.dsl.module

val appModule = module {
    single { AppConfig() }
    single { JwtService(get()) }

    single<EventRepository> { EventRepositoryImpl() }
    single<OrganizerRepository> { OrganizerRepositoryImpl() }
    single<UserRepository> { UserRepositoryImpl() }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl() }

    factory { GetEventsUseCase(get()) }
    factory { GetEventUseCase(get()) }
    factory { GetOrganizerUseCase(get()) }
    factory { GetOrganizerEventsUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { IssueTokensUseCase(get(), get()) }
    factory { RefreshSessionUseCase(get(), get()) }
    factory { LogoutUseCase(get()) }
}
