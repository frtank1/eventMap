package com.eventmap.core.files

/**
 * Открывает скачанный файл системным viewer.
 * - Android: `Intent.ACTION_VIEW` + `FileProvider` для `file://Downloads/…`.
 * - iOS: `UIActivityViewController` (он же открывает / расшаривает).
 */
expect class FileOpener {
    fun open(localUri: String, mimeType: String? = null)
}
