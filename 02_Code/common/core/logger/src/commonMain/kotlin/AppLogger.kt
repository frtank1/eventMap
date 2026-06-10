package com.eventmap.core.logger

import kotlin.time.Clock

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val epochMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val error: String? = null,
)

// Платформенный вывод: android -> Logcat, ios/desktop/wasm -> println/console.
internal expect fun writePlatformLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)

/**
 * Кроссплатформенный логгер. Пишет в нативную консоль платформы и
 * одновременно копит последние записи в кольцевой буфер, откуда их
 * можно вытащить ([dump]/[entries]) — для баг-репортов, сохранения в файл и т.п.
 */
object AppLogger {
    private const val MAX_ENTRIES = 500
    private val buffer = ArrayDeque<LogEntry>()

    /** Ниже этого уровня логи игнорируются. В релизе обычно INFO/WARN. */
    var minLevel: LogLevel = LogLevel.DEBUG

    /** Подменяется в приложении на Crashlytics/Sentry-реализацию. */
    var crashReporter: CrashReporter = NoopCrashReporter

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        writePlatformLog(level, tag, message, throwable)
        buffer.addLast(
            LogEntry(
                epochMillis = Clock.System.now().toEpochMilliseconds(),
                level = level,
                tag = tag,
                message = message,
                error = throwable?.message,
            )
        )
        while (buffer.size > MAX_ENTRIES) buffer.removeFirst()

        // форвард в краш-репортинг: WARN -> хлебная крошка, ERROR -> запись исключения
        when (level) {
            LogLevel.WARN -> crashReporter.log("[$tag] $message")
            LogLevel.ERROR -> {
                if (throwable != null) crashReporter.recordException(throwable, "[$tag] $message")
                else crashReporter.log("[$tag] $message")
            }
            else -> Unit
        }
    }

    /** Снимок последних записей. */
    fun entries(): List<LogEntry> = buffer.toList()

    /** Все логи одной строкой — удобно сохранить в файл или приложить к баг-репорту. */
    fun dump(): String = buffer.joinToString("\n") { entry ->
        "${entry.epochMillis} ${entry.level.name.padEnd(5)} [${entry.tag}] ${entry.message}" +
            (entry.error?.let { " | $it" } ?: "")
    }

    fun clear() = buffer.clear()
}
