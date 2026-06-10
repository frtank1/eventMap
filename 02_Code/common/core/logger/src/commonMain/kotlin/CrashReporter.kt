package com.eventmap.core.logger

/**
 * Точка интеграции с краш-репортингом (Crashlytics / Sentry / свой бэкенд).
 * По умолчанию — заглушка. В приложении подменяешь одной реализацией:
 *   AppLogger.crashReporter = MyCrashlyticsReporter()
 * и привязываешь в Koin вместо NoopCrashReporter.
 */
interface CrashReporter {
    fun log(message: String)
    fun recordException(throwable: Throwable, message: String? = null)
    fun setUserId(id: String?)
}

object NoopCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordException(throwable: Throwable, message: String?) = Unit
    override fun setUserId(id: String?) = Unit
}
