package com.eventmap.core.platform.geo

import PlatformConfiguration
import org.koin.dsl.module

// =====================================================================
//  Геолокация (one-shot): гео-чекины квестов Q3, «Я тоже здесь» D1c,
//  гео-пинг /map/ping (I2). Разрешения: Android — ACCESS_FINE_LOCATION
//  (запрашивает MainActivity), iOS — NSLocationWhenInUseUsageDescription.
// =====================================================================

data class GeoFix(val lat: Double, val lng: Double)

interface GeoProvider {
    /**
     * Текущая позиция. null — нет разрешения / гео недоступно на платформе.
     * Реализации не бросают исключений — только null.
     */
    suspend fun current(): GeoFix?
}

expect fun createGeoProvider(configuration: PlatformConfiguration): GeoProvider

val geoModule = module {
    single<GeoProvider> { createGeoProvider(get()) }
}
