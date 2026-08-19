package com.eventmap.views.media

// Wasm: файловой системы нет — превью ролика недоступно (тайл покажет подпись)
actual fun writeTempMedia(bytes: ByteArray, extension: String): String? = null
