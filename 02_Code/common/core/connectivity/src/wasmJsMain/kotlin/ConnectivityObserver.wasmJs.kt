package com.eventmap.core.connectivity

import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.module.Module
import org.koin.dsl.module
import org.w3c.dom.events.Event

actual class ConnectivityObserver {
    actual val isOnline: Flow<Boolean> = callbackFlow {
        trySend(window.navigator.onLine)
        val onOnline: (Event) -> Unit = { trySend(true) }
        val onOffline: (Event) -> Unit = { trySend(false) }
        window.addEventListener("online", onOnline)
        window.addEventListener("offline", onOffline)
        awaitClose {
            window.removeEventListener("online", onOnline)
            window.removeEventListener("offline", onOffline)
        }
    }.distinctUntilChanged()

    actual fun isOnlineNow(): Boolean = window.navigator.onLine
}

actual val connectivityModule: Module = module {
    single { ConnectivityObserver() }
}
