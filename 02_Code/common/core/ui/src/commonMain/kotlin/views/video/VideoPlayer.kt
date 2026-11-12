package com.eventmap.views.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// =====================================================================
//  Кроссплатформенный видеоплеер (рилсы E1/E2, D1).
//  Android — media3/ExoPlayer, iOS — AVPlayer, desktop/wasm — заглушка-постер.
//  Контролы рисует экран; состояние времени приходит от реального плеера.
// =====================================================================

@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    muted: Boolean = false,
    loop: Boolean = true,
    onPlaybackState: (VideoPlaybackState) -> Unit = {},
)

data class VideoPlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val buffering: Boolean = false,
    val failed: Boolean = false,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

fun videoTimeLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
