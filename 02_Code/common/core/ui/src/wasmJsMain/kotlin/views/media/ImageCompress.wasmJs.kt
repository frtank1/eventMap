package com.eventmap.views.media

// Wasm: без canvas-обвязки сжатие недоступно — отдаём исходные байты.
actual fun compressImage(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray = bytes
