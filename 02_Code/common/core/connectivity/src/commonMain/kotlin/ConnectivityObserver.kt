package com.eventmap.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Наблюдение за сетью. [isOnline] — реактивный поток, [isOnlineNow] — снимок.
 * android/wasm — реактивно; ios/desktop — best-effort (всегда online, см. TODO в actual).
 */
expect class ConnectivityObserver {
    val isOnline: Flow<Boolean>
    fun isOnlineNow(): Boolean
}
