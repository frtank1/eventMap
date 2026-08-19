package com.eventmap.views.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerLooper
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.UIKit.UIView

// AVQueuePlayer + AVPlayerLooper: бесшовный loop, aspect-fill, без контролов
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    playing: Boolean,
    muted: Boolean,
    loop: Boolean,
) {
    val player = remember(url) { AVQueuePlayer() }
    // looper держим в памяти, пока жив плеер — иначе loop отвалится
    val looper = remember(url) {
        val nsUrl = NSURL.URLWithString(url) ?: return@remember null
        val item = AVPlayerItem(uRL = nsUrl)
        if (loop) {
            AVPlayerLooper.playerLooperWithPlayer(player, templateItem = item)
        } else {
            player.insertItem(item, afterItem = null)
            null
        }
    }

    LaunchedEffect(player, playing) { if (playing) player.play() else player.pause() }
    LaunchedEffect(player, muted) { player.muted = muted }
    DisposableEffect(player) {
        onDispose {
            player.pause()
            player.removeAllItems()
        }
    }

    UIKitView(
        factory = { PlayerContainerView(player) },
        modifier = modifier,
    )

    // подавляем "unused" — looper обязан жить в композиции
    @Suppress("UNUSED_EXPRESSION") looper
}

// UIView с AVPlayerLayer на всю площадь (layoutSubviews следит за размером)
@OptIn(ExperimentalForeignApi::class)
private class PlayerContainerView(player: AVQueuePlayer) : UIView(frame = CGRectZero.readValue()) {

    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
}
