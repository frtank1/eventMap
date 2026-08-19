package com.eventmap.views.media

import java.io.File

// JVM: File.createTempFile использует java.io.tmpdir (на Android это cache-директория приложения)
actual fun writeTempMedia(bytes: ByteArray, extension: String): String? = runCatching {
    val file = File.createTempFile("preview-", extension)
    file.deleteOnExit()
    file.writeBytes(bytes)
    "file://${file.absolutePath}"
}.getOrNull()
