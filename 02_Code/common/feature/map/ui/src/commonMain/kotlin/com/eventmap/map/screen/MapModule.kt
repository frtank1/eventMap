package com.eventmap.map.screen

import com.eventmap.core.network.CoreConfig
import com.eventmap.map.data.mapPulseModule
import org.koin.dsl.module

val mapModule = module {
    includes(mapPulseModule) // /map/pulse, /map/fog, /map/ping (G1–G3, G9)
    factory {
        MapViewModel(
            eventRepository = get(),
            pulseRepository = get(),
            geo = get(),           // core.platform.geoModule
            pulseSettings = get(), // core.datastore (тумблер I2)
            demoMode = get<CoreConfig>().demoMode,
        )
    }
}
