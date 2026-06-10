package com.eventmap.core.utils

/**
 * Перечень поддерживаемых языков приложения.
 *
 * Если добавляешь новый язык — добавь сюда и не разноси теги по
 * хардкодным строкам в коде; enum даёт компиляторный контроль.
 */
enum class AppLocale(val tag: String, val acceptLanguage: String) {
    KK(tag = "kk", acceptLanguage = "kk-KZ"),
    RU(tag = "ru", acceptLanguage = "ru-RU");

    companion object {
        val DEFAULT: AppLocale = KK

        fun fromTag(tag: String?): AppLocale =
            entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
