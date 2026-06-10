package com.eventmap.example.ui

import com.eventmap.example.ui.domain.LoginUseCase
import org.koin.dsl.module

val exampleModule = module {
    factory { LoginUseCase(repository = get()) }
    factory { ExampleViewModel(loginUseCase = get()) }
}
