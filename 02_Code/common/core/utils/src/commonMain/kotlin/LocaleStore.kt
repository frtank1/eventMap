package com.eventmap.core.utils

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище выбранного языка. Полностью кросс-платформенное:
 * на Android живёт в SharedPreferences, на iOS — в NSUserDefaults,
 * на JVM — в java.util.prefs.
 *
 * Реактивно: [current] — StateFlow, на который удобно подписаться
 * из Compose (`collectAsState`) или из репозиториев.
 */
class LocaleStore(private val settings: Settings) {

    private val _current = MutableStateFlow(
        AppLocale.fromTag(settings.getStringOrNull(KEY))
    )
    val current: StateFlow<AppLocale> = _current.asStateFlow()

    fun get(): AppLocale = _current.value

    fun set(locale: AppLocale) {
        settings.putString(KEY, locale.tag)
        _current.value = locale
    }

    companion object {
        const val KEY: String = "lang_app"
    }
}

/**
 * Выбрать локализованный текст по текущему языку — аналог
 * ParserHelper.getActualLocalText(kz, ru) из старого Android-кода.
 */
fun pickByLocale(locale: AppLocale, kz: String?, ru: String?): String =
    when (locale) {
        AppLocale.KK -> kz ?: ru.orEmpty()
        AppLocale.RU -> ru ?: kz.orEmpty()
    }
