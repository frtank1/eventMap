package com.eventmap.views.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Desktop: без нативного плеера — постер-заглушка (видео смотрят в мобильном приложении)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    playing: Boolean,
    muted: Boolean,
    loop: Boolean,
) {
    Box(modifier.background(Color(0xFF0D0818)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▶", fontSize = 48.sp, color = Color.White.copy(alpha = 0.25f))
            Text("Видео — в мобильном приложении", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
        }
    }
}
