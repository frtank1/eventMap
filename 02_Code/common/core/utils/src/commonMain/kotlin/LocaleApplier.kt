package com.eventmap.core.utils

/**
 * Применение языка к ресурсам ОС. Реализация платформозависимая:
 * Android — Configuration / AppCompatDelegate / LocaleManager,
 * iOS — выставляет AppleLanguages в NSUserDefaults (требует
 * перезапуск приложения, см. [needsRestart]),
 * Desktop — Locale.setDefault.
 */
expect class LocaleApplier {

    /**
     * Применить язык к текущей среде. Возвращает true, если изменения
     * вступили в силу сразу, и false — если нужен перезапуск
     * приложения (актуально для iOS).
     */
    fun apply(locale: AppLocale): Boolean

    /** Платформа требует полный рестарт приложения для смены языка. */
    val needsRestart: Boolean
}

/**
 * Удобный фасад: сохранить язык в [LocaleStore] и сразу применить
 * через [LocaleApplier]. В DI-графе обычно собирают один такой
 * сервис и инжектят в UI-слой.
 */
class LocaleManager(
    private val store: LocaleStore,
    private val applier: LocaleApplier,
) {
    val current = store.current
    fun get(): AppLocale = store.get()
    fun acceptLanguage(): String = store.get().acceptLanguage

    /** Сохраняет язык и применяет его. Возвращает результат [LocaleApplier.apply]. */
    fun change(locale: AppLocale): Boolean {
        store.set(locale)
        return applier.apply(locale)
    }
}
