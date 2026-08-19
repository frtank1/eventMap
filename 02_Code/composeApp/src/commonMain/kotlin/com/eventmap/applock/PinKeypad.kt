package com.eventmap.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.theme.Danger
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

private val KEY_SIZE = 72.dp

/** Точки-индикатор введённых цифр. */
@Composable
internal fun PinDots(
    filled: Int,
    total: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(total) { index ->
            val active = index < filled
            val color = when {
                error -> Danger
                active -> Violet600
                else -> Color.White.copy(alpha = 0.15f)
            }
            Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        }
    }
}

/** Цифровая клавиатура 3×4. Левый нижний слот опционален (например, биометрия). */
@Composable
internal fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    leftSlot: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(1..3, 4..6, 7..9).forEach { range ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                range.forEach { digit -> KeyCircle(digit.toString()) { onDigit(digit) } }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                leftSlot?.invoke()
            }
            KeyCircle("0") { onDigit(0) }
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(KEY_SIZE).clip(CircleShape).clickableNoRipple(onBackspace),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⌫", color = NightTextSecondary, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun KeyCircle(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .background(NightSurface)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}
