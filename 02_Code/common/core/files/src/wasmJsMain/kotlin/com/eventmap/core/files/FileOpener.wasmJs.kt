package com.eventmap.core.files

actual class FileOpener {
    actual fun open(localUri: String, mimeType: String?) {
        // web: можно открыть window.open(localUri)
    }
}
