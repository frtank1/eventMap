package com.eventmap.core.files

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class FileDownloaderFactory {
    actual fun create(): FileDownloader = DesktopFileDownloader()
}

/** На desktop пока не используется — возвращаем no-op. */
private class DesktopFileDownloader : FileDownloader {
    override fun download(
        url: String,
        fileName: String,
        mimeType: String?,
    ): Flow<DownloadProgress> = flowOf(
        DownloadProgress.Failure("Скачивание на desktop пока не поддержано")
    )
}
