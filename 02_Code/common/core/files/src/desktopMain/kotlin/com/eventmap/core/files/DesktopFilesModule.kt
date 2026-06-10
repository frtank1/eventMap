package com.eventmap.core.files

import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformFilesModule: Module = module {
    single { FileDownloaderFactory() }
    single { FileOpener() }
}
