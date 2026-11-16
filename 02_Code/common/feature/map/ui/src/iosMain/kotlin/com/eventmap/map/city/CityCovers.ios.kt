package com.eventmap.map.city

import com.eventmap.views.media.compressImage
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
internal actual fun cityCoverThumbnail(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
    val image = UIImage(data = data)
    val pixels = image.size.useContents { width * height * image.scale * image.scale }
    if (pixels <= 0 || pixels > 16_000_000) return null
    return compressImage(bytes, 192, 72)
}
