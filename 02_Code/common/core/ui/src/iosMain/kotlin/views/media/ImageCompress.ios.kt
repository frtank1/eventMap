package com.eventmap.views.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun compressImage(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray = runCatching {
    val data = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
    val image = UIImage(data = data)
    val width = image.size.useContents { width }
    val height = image.size.useContents { height }
    val longest = maxOf(width, height)
    val scaled: UIImage = if (longest > maxDim.toDouble()) {
        val scale = maxDim.toDouble() / longest
        val newSize = CGSizeMake(width * scale, height * scale)
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
        val result = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        result ?: image
    } else {
        image
    }
    val jpeg = UIImageJPEGRepresentation(scaled, quality / 100.0) ?: return bytes
    val out = ByteArray(jpeg.length.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    }
    out
}.getOrDefault(bytes)
