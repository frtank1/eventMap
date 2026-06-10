package com.eventmap.core.files

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Общая часть. Фабрика [FileDownloaderFactory] собирается в платформенном
 * модуле ([platformFilesModule]), здесь экспортируем готовый [FileDownloader].
 */
val filesModule = module {
    single<FileDownloader> { get<FileDownloaderFactory>().create() }
}

/** Платформенный модуль регистрирует [FileDownloaderFactory]. */
expect val platformFilesModule: Module
