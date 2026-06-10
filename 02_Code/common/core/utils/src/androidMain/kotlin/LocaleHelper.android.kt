package com.eventmap.core.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Android-реализация. Использует AppCompatDelegate, который сам
 * выбирает корректный механизм: LocaleManager на API 33+ и
 * совместимый путь через Configuration на старых версиях.
 *
 * Никаких Activity / Context здесь не требуется — AndroidX делает
 * это сам, и пересоздание Activity тоже на его стороне.
 */
actual class LocaleApplier {

    actual fun apply(locale: AppLocale): Boolean {
        Locale.setDefault(Locale.forLanguageTag(locale.tag))
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.tag)
        )
        return true
    }

    actual val needsRestart: Boolean = false
}
