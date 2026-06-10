package com.eventmap.core.files

/**
 * Платформенная фабрика. На Android в конструктор принимает Context,
 * на iOS — пустой. Koin собирает её сам, см. `filesModule`.
 */
expect class FileDownloaderFactory {
    fun create(): FileDownloader
}
