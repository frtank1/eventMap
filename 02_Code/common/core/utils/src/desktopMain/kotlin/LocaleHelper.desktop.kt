package com.eventmap.core.utils

import java.util.Locale

/**
 * Desktop-реализация. JVM умеет сразу менять Locale.default,
 * остальное (перерисовка экранов) — забота вызывающего слоя
 * (например, Compose recomposition по StateFlow из LocaleStore).
 */
actual class LocaleApplier {

    actual fun apply(locale: AppLocale): Boolean {
        Locale.setDefault(Locale.forLanguageTag(locale.tag))
        return true
    }

    actual val needsRestart: Boolean = false
}
