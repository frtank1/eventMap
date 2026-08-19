package com.eventmap.core.platform.reminders

import PlatformConfiguration
import org.koin.dsl.module

// =====================================================================
//  Локальные напоминания (без бэка): «за час до события».
//  Android — WorkManager + Notification, iOS — UNUserNotificationCenter,
//  desktop/wasm — no-op. Время в прошлом — тихо игнорируется.
// =====================================================================

interface LocalReminders {
    /** Запланировать разовое уведомление. Повторный вызов с тем же id заменяет старое. */
    fun schedule(id: String, title: String, body: String, atEpochMs: Long)
    fun cancel(id: String)
}

expect fun createLocalReminders(configuration: PlatformConfiguration): LocalReminders

val remindersModule = module {
    single<LocalReminders> { createLocalReminders(get()) }
}
