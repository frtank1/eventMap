package com.eventmap.core.files

import com.eventmap.core.network.CoreConfig
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformFilesModule: Module = module {
    single {
        FileDownloaderFactory(
            tokenStorage = get(),
            baseUrl = get<CoreConfig>().baseUrl,
        )
    }
    single { FileOpener() }
}
