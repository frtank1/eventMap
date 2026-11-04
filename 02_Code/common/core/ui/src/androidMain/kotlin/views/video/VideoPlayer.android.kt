package com.eventmap.views.video

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var buffering by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            // Do not prefetch adjacent reels before they become visible (delivery is billable).
        }
    }

    LaunchedEffect(exoPlayer, playing, lifecycle) {
        val active = playing && lifecycle.isAtLeast(Lifecycle.State.STARTED)
        if (active && exoPlayer.playbackState == Player.STATE_IDLE && !failed) exoPlayer.prepare()
        exoPlayer.playWhenReady = active
    }
    LaunchedEffect(exoPlayer, muted) { exoPlayer.volume = if (muted) 0f else 1f }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    Box(modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = Modifier.fillMaxSize(),
    )
        if (failed) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Не удалось загрузить видео", color = Color.White)
            TextButton(onClick = { failed = false; buffering = true; exoPlayer.prepare() }) { Text("Повторить") }
            Text("Если не помогло — обновите экран", color = Color.White)
        } else if (buffering && playing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
}
