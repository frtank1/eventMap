package com.eventmap

import PlatformConfiguration
import android.app.Application

class EventMapApp : Application() {

    override fun onCreate() {
        super.onCreate()

        PlatformSDK.init(
            configuration = PlatformConfiguration(
                androidContext = applicationContext
            )
        )
    }
}
