package com.eventmap

import androidx.compose.ui.window.ComposeUIViewController
import PlatformConfiguration


fun MainViewController() = ComposeUIViewController {
    PlatformSDK.init(
        configuration = PlatformConfiguration()
    )
    App()
}