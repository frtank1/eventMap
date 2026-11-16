package com.eventmap.map.city

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun cityCoverThumbnail(bytes: ByteArray): ByteArray? = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
    val readers = ImageIO.getImageReaders(input)
    if (!readers.hasNext()) return null
    val reader = readers.next()
    try {
        reader.input = input
        val w = reader.getWidth(0); val h = reader.getHeight(0)
        if (w <= 0 || h <= 0 || w.toLong() * h > 40_000_000) return null
        val param = reader.defaultReadParam.apply { val sample = maxOf(1, maxOf(w, h) / 192); setSourceSubsampling(sample, sample, 0, 0) }
        val source = reader.read(0, param)
        val ratio = minOf(1.0, 192.0 / maxOf(source.width, source.height))
        val out = BufferedImage(maxOf(1, (source.width * ratio).toInt()), maxOf(1, (source.height * ratio).toInt()), BufferedImage.TYPE_INT_RGB)
        val graphics = out.createGraphics()
        try { graphics.drawImage(source, 0, 0, out.width, out.height, null) } finally { graphics.dispose(); source.flush() }
        ByteArrayOutputStream().use { stream -> ImageIO.write(out, "jpeg", stream); out.flush(); stream.toByteArray() }
    } finally { reader.dispose() }
}
