package com.eventmap.core.platform.geo

import PlatformConfiguration

// Wasm: browser geolocation API — позже (нужен permission-flow браузера).
actual fun createGeoProvider(configuration: PlatformConfiguration): GeoProvider =
    object : GeoProvider {
        override suspend fun current(): GeoFix? = null
    }
