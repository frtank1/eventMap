package com.eventmap.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Единый набор иконок EventMap («живой город» v1).
// Глифы заданы в системе координат 120×120 (как в Figma-проекте Иконки v1),
// рисуются белым и тонируются в местах использования (Icon tint).
private fun glyph(path: String, fillType: PathFillType = PathFillType.NonZero): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 120f,
        viewportHeight = 120f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.White),
        pathFillType = fillType,
    ).build()

object EventMapIcons {
    // Навигация
    val Map: ImageVector = glyph(
        "M60 30c-13 0-23 10-23 23 0 16 23 37 23 37s23-21 23-37c0-13-10-23-23-23z " +
            "M60 44a9 9 0 100 18 9 9 0 100-18z",
        PathFillType.EvenOdd,
    )
    val Reels: ImageVector = glyph(
        "M50 33h20a17 17 0 0117 17v20a17 17 0 01-17 17H50a17 17 0 01-17-17V50a17 17 0 0117-17z " +
            "M53 48l22 12-22 12z",
        PathFillType.EvenOdd,
    )
    val Video: ImageVector = glyph(
        "M40 46h8l4-6h16l4 6h8a10 10 0 0110 10v22a10 10 0 01-10 10H40a10 10 0 01-10-10V56a10 10 0 0110-10z " +
            "M60 58a11 11 0 100 22 11 11 0 100-22z",
        PathFillType.EvenOdd,
    )
    val Profile: ImageVector = glyph(
        "M60 33a15 15 0 100 30 15 15 0 100-30z M34 92c0-15 12-24 26-24s26 9 26 24z",
    )

    // Дополнительно (категории / экшены) — для следующего этапа
    val Flame: ImageVector = glyph(
        "M63 28c11 13 17 21 17 33a20 20 0 11-40 0c0-9 5-15 11-19-2 9 4 13 9 11-5-11 1-19 3-25z",
    )
    val Bell: ImageVector = glyph(
        "M60 28a19 19 0 00-19 19c0 17-7 23-7 23h52s-7-6-7-23a19 19 0 00-19-19z " +
            "M60 78a8 8 0 100 16 8 8 0 100-16z",
        PathFillType.EvenOdd,
    )
    val Plus: ImageVector = glyph("M51 32H69V51H88V69H69V88H51V69H32V51H51Z")
    val Heart: ImageVector = glyph("M60 86C44 74 33 64 33 50a13 13 0 0127-9 13 13 0 0127 9c0 14-11 24-27 36z")
    val Comment: ImageVector = glyph(
        "M36 40h48a7 7 0 017 7v22a7 7 0 01-7 7H56l-13 11v-11h-7a7 7 0 01-7-7V47a7 7 0 017-7z",
    )
    val Share: ImageVector = glyph("M88 32L32 55l21 7 6 21 9-14 16 12z")
    val Food: ImageVector = glyph(
        "M36 50c0-9 11-16 24-16s24 7 24 16z M36 58h48v9H36z M36 74c0 9 11 14 24 14s24-5 24-14z",
    )
    val Leaf: ImageVector = glyph("M42 82c-2-26 16-48 40-48 2 26-12 50-40 48z")
}

/** Цветная плитка-сквиркл с белым глифом — «живой» стиль для категорий/входных точек. */
@Composable
fun GradientIconTile(
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier.size(56.dp),
) {
    Box(
        modifier.clip(RoundedCornerShape(percent = 30)).background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.62f))
    }
}
