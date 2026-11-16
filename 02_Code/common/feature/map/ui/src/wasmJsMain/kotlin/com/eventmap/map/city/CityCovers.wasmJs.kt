package com.eventmap.map.city

// The native game-city view is mobile-only; no hidden image downloads on this target.
internal actual fun cityCoverThumbnail(bytes: ByteArray): ByteArray? = null
