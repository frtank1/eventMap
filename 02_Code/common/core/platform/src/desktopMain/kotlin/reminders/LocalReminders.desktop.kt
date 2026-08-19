package com.eventmap.core.platform.reminders

import PlatformConfiguration

// Desktop: системных нотификаций в этом стеке нет — no-op.
actual fun createLocalReminders(configuration: PlatformConfiguration): LocalReminders =
    object : LocalReminders {
        override fun schedule(id: String, title: String, body: String, atEpochMs: Long) = Unit
        override fun cancel(id: String) = Unit
    }
