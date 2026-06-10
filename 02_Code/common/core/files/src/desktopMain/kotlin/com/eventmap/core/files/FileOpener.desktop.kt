package com.eventmap.core.files

actual class FileOpener {
    actual fun open(localUri: String, mimeType: String?) {
        // На desktop пока не реализовано — Compose Multiplatform Desktop
        // не используется в проде. Можно добавить java.awt.Desktop.open(...)
    }
}
