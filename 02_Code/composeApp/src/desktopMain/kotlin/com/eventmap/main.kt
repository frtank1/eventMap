package com.eventmap

import PlatformConfiguration
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "example",
    ) {
        PlatformSDK.init(
            configuration = PlatformConfiguration()
        )
        App()
    }
}