package com.eventmap.core.utils

// web: смена языка — забота i18n-слоя/перезагрузки страницы
actual class LocaleApplier {
    actual fun apply(locale: AppLocale): Boolean = true
    actual val needsRestart: Boolean = false
}
