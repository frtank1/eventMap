package com.eventmap.views.media

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual fun compressImage(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray = runCatching {
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
    val longest = maxOf(source.width, source.height)
    val scaled: BufferedImage = if (longest > maxDim) {
        val scale = maxDim.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { target ->
            val g = target.createGraphics()
            g.drawImage(source.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            g.dispose()
        }
    } else if (source.type != BufferedImage.TYPE_INT_RGB) {
        // JPEG не умеет альфу — перерисовываем в RGB
        BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB).also { target ->
            val g = target.createGraphics()
            g.drawImage(source, 0, 0, java.awt.Color.BLACK, null)
            g.dispose()
        }
    } else {
        source
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(scaled, "jpg", out)
    out.toByteArray()
}.getOrDefault(bytes)
