package com.eventmap.core.network.error

import kotlinx.coroutines.flow.MutableSharedFlow

class GlobalErrorBus {
    val events = MutableSharedFlow<GlobalError>(extraBufferCapacity = 1)
}


sealed class GlobalError {
    object Unauthorized : GlobalError()
    data class ServerError(val code: Int) : GlobalError()
}