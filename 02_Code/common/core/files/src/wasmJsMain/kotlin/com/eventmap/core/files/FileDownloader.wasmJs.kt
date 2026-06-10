package com.eventmap.core.files

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class FileDownloaderFactory {
    actual fun create(): FileDownloader = WasmFileDownloader()
}

// web: пока no-op. Реально на web обычно <a download> / Blob URL.
private class WasmFileDownloader : FileDownloader {
    override fun download(
        url: String,
        fileName: String,
        mimeType: String?,
    ): Flow<DownloadProgress> = flowOf(
        DownloadProgress.Failure("Скачивание на web пока не поддержано")
    )
}
