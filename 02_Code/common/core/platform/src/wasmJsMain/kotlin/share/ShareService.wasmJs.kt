package com.eventmap.core.platform.share

import PlatformConfiguration

// Wasm: Web Share API требует user gesture + secure context — в базовой сборке no-op.
actual fun createShareService(configuration: PlatformConfiguration): ShareService =
    object : ShareService {
        override fun shareText(text: String) = Unit
    }
