package com.eventmap.views.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

actual fun compressImage(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray = runCatching {
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val longest = maxOf(source.width, source.height)
    val scaled = if (longest > maxDim) {
        val scale = maxDim.toFloat() / longest
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== source) scaled.recycle()
    source.recycle()
    out.toByteArray()
}.getOrDefault(bytes)
