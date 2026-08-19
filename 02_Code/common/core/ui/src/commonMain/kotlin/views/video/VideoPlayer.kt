package com.eventmap.views.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// =====================================================================
//  Кроссплатформенный видеоплеер (рилсы E1/E2, D1).
//  Android — media3/ExoPlayer, iOS — AVPlayer, desktop/wasm — заглушка-постер.
//  Управления нет намеренно (тикток-стиль): loop + автоплей, звук по флагу.
// =====================================================================

@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    muted: Boolean = false,
    loop: Boolean = true,
)
