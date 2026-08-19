package com.eventmap.views.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// ExoPlayer: loop, автоплей, без контроллера — кадр заполняет экран (RESIZE_MODE_ZOOM)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // PlayerView.resizeMode
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    playing: Boolean,
    muted: Boolean,
    loop: Boolean,
) {
    val context = LocalContext.current

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    LaunchedEffect(exoPlayer, playing) { exoPlayer.playWhenReady = playing }
    LaunchedEffect(exoPlayer, muted) { exoPlayer.volume = if (muted) 0f else 1f }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = modifier,
    )
}
