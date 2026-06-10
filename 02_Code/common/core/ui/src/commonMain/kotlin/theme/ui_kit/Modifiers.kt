package com.eventmap.theme.ui_kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/** clickable без ripple-эффекта — для кастомных кнопок/градиентов. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/**
 * clickable без ripple + пружинное сжатие при нажатии (scale 0.96).
 * Стандартный press-фидбек кнопок ui-kit.
 */
fun Modifier.pressableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 600f),
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}
