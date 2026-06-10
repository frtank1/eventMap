package com.eventmap.core.files

import PlatformConfiguration
import com.eventmap.core.network.CoreConfig
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformFilesModule: Module = module {
    single {
        FileDownloaderFactory(
            context = get<PlatformConfiguration>().androidContext,
            tokenStorage = get(),
            baseUrl = get<CoreConfig>().baseUrl,
        )
    }

    single {
        FileOpener(
            context = get<PlatformConfiguration>().androidContext,
        )
    }
}
