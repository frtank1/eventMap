package com.eventmap.map.city

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal actual fun cityCoverThumbnail(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 40_000_000) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    var scaled: Bitmap? = null
    return try {
        val ratio = minOf(1f, 192f / maxOf(bitmap.width, bitmap.height))
        scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()), maxOf(1, (bitmap.height * ratio).toInt()), true)
        ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 72, out); out.toByteArray() }
    } finally { if (scaled !== bitmap) scaled?.recycle(); bitmap.recycle() }
}
