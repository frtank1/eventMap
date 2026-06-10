package com.eventmap.core.logger

internal actual fun writePlatformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    val suffix = throwable?.let { " | ${it.message}" } ?: ""
    println("[${level.name}] [$tag] $message$suffix")
}
