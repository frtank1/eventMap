package com.eventmap.core.platform.share

import PlatformConfiguration
import org.koin.dsl.module

// =====================================================================
//  Системный шаринг текста: Android — Intent.ACTION_SEND (chooser),
//  iOS — UIActivityViewController, desktop — буфер обмена, wasm — no-op.
// =====================================================================

interface ShareService {
    fun shareText(text: String)
}

expect fun createShareService(configuration: PlatformConfiguration): ShareService

val shareModule = module {
    single<ShareService> { createShareService(get()) }
}
