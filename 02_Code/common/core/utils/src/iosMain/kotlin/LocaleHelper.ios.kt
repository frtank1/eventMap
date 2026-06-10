package com.eventmap.core.utils

import platform.Foundation.NSUserDefaults

/**
 * iOS-реализация. Apple не даёт сменить язык приложения «налету»,
 * AppleLanguages читается системой только при старте процесса —
 * поэтому apply() возвращает false, а вызывающий код должен показать
 * алерт «перезапустите приложение».
 */
actual class LocaleApplier {

    actual fun apply(locale: AppLocale): Boolean {
        NSUserDefaults.standardUserDefaults.setObject(
            value = listOf(locale.tag),
            forKey = APPLE_LANGUAGES_KEY,
        )
        return false
    }

    actual val needsRestart: Boolean = true

    private companion object {
        const val APPLE_LANGUAGES_KEY = "AppleLanguages"
    }
}
