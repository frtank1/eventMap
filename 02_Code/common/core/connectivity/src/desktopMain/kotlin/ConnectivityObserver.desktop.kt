package com.eventmap.core.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

// TODO: при необходимости — реальная проверка (InetAddress/socket).
actual class ConnectivityObserver {
    actual val isOnline: Flow<Boolean> = flowOf(true)
    actual fun isOnlineNow(): Boolean = true
}

actual val connectivityModule: Module = module {
    single { ConnectivityObserver() }
}
