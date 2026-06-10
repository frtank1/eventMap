package com.eventmap.core.logger

import android.util.Log

internal actual fun writePlatformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, message, throwable)
        LogLevel.INFO -> Log.i(tag, message, throwable)
        LogLevel.WARN -> Log.w(tag, message, throwable)
        LogLevel.ERROR -> Log.e(tag, message, throwable)
    }
}
