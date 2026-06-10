package com.eventmap.theme.ui_kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.Motion

enum class EventStatus { Live, Soon, Going, Was }

/**
 * Пилюля статуса события: LIVE (с пульсирующей точкой), СКОРО, ИДУ, БЫЛ.
 * Использует семантические токены из [EventMapTheme.colors].
 */
@Composable
fun StatusPill(
    status: EventStatus,
    modifier: Modifier = Modifier,
) {
    val colors = EventMapTheme.colors
    val (bg, label) = when (status) {
        EventStatus.Live -> colors.live to "LIVE"
        EventStatus.Soon -> colors.warning to "СКОРО"
        EventStatus.Going -> colors.soft to "ИДУ"
        EventStatus.Was -> colors.textSecondary to "БЫЛ"
    }
    // тёмный текст на светло-жёлтом «СКОРО», белый — на остальных
    val onBg = if (status == EventStatus.Soon) Color(0xFF1A1300) else Color.White

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (status == EventStatus.Live) {
            val transition = rememberInfiniteTransition()
            val dotAlpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(Motion.FLICKER), RepeatMode.Reverse),
            )
            Spacer(
                Modifier
                    .size(6.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            Spacer(Modifier.size(5.dp))
        }
        Text(
            text = label,
            color = onBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
