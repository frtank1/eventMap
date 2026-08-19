package com.eventmap.core.platform.geo

import PlatformConfiguration

// Desktop: системной геолокации нет — чек-ины по гео недоступны (UI покажет подсказку).
actual fun createGeoProvider(configuration: PlatformConfiguration): GeoProvider =
    object : GeoProvider {
        override suspend fun current(): GeoFix? = null
    }
