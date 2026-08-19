package com.eventmap.views.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun writeTempMedia(bytes: ByteArray, extension: String): String? = runCatching {
    val path = NSTemporaryDirectory() + "preview-${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}$extension"
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned {
        NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong())
    }
    if (data.writeToFile(path, atomically = true)) "file://$path" else null
}.getOrNull()
