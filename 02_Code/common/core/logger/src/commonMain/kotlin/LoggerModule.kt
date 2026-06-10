package com.eventmap.core.logger

import org.koin.dsl.module

val loggerModule = module {
    single { AppLogger }
    // По умолчанию заглушка. В приложении переопредели на свою реализацию
    // (Crashlytics/Sentry) и не забудь AppLogger.crashReporter = get().
    single<CrashReporter> { NoopCrashReporter }
}
