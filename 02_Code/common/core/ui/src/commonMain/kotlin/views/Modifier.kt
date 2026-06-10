package com.eventmap.views

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventmap.theme.white

fun Modifier.card(
    elevation: Dp = 4.dp,
    color: Color = white,
    roundedCorner: Dp = 16.dp
): Modifier {
    return this.shadow(elevation = elevation, shape = RoundedCornerShape(roundedCorner))
        .background(color = color, shape = RoundedCornerShape(roundedCorner))
}