package com.eventmap.theme

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единый набор бесплатных встроенных анимаций пина (Этап 1).
 * Переиспользуется и на карте, и в превью выбора анимации в бизнес-пине.
 */
enum class PinEffect { None, Pulse, Wave, Glow, Blink }

/** Сопоставление названий из UI-пикера с эффектом. */
fun pinEffectByName(name: String): PinEffect = when (name) {
    "Пульс" -> PinEffect.Pulse
    "Волна" -> PinEffect.Wave
    "Свечение" -> PinEffect.Glow
    "Мигание" -> PinEffect.Blink
    else -> PinEffect.None
}

/** Анимированный пин: цветной кружок с эмодзи и эффектом позади. */
@Composable
fun AnimatedPin(
    effect: PinEffect,
    color: Color,
    emoji: String,
    size: Dp = 56.dp,
    emojiSize: TextUnit = 24.sp,
) {
    val transition = rememberInfiniteTransition()
    val blink by transition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    val markerAlpha = if (effect == PinEffect.Blink) blink else 1f

    Box(contentAlignment = Alignment.Center) {
        when (effect) {
            PinEffect.Pulse, PinEffect.Wave -> {
                PinRing(transition, color, size, 2200)
                if (effect == PinEffect.Wave) PinRing(transition, color, size, 1500)
            }
            PinEffect.Glow -> {
                val g by transition.animateFloat(
                    initialValue = 1.1f, targetValue = 1.7f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                )
                val a by transition.animateFloat(
                    initialValue = 0.35f, targetValue = 0.08f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                )
                Box(Modifier.size(size).scale(g).alpha(a).clip(CircleShape).background(color))
            }
            PinEffect.None, PinEffect.Blink -> Unit
        }

        Box(
            Modifier.size(size).alpha(markerAlpha).clip(CircleShape)
                .background(color.copy(alpha = 0.25f)).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = emojiSize)
        }
    }
}

@Composable
private fun PinRing(transition: InfiniteTransition, color: Color, size: Dp, durationMs: Int) {
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Restart),
    )
    val a by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Restart),
    )
    Box(Modifier.size(size).scale(pulse).alpha(a).clip(CircleShape).background(color))
}
