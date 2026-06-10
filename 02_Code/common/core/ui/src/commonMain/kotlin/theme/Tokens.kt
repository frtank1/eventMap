package com.eventmap.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------
//  Spacing — базовая шкала 4dp (DESIGN_TOKENS §3)
// ---------------------------------------------------------------------
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val huge: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// ---------------------------------------------------------------------
//  Radius (DESIGN_TOKENS §4)
// ---------------------------------------------------------------------
object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
}

// ---------------------------------------------------------------------
//  Анимации (DESIGN_TOKENS §7)
// ---------------------------------------------------------------------
object Motion {
    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 500
    const val PULSE = 2200
    const val BREATHE = 3000
    const val FLICKER = 1000
}

// ---------------------------------------------------------------------
//  Семантические цвета вне Material ColorScheme (карта, статусы, текст-уровни).
//  Меняются между day/night, бренд-якоря (live/soft/warning) — постоянны.
// ---------------------------------------------------------------------
@Immutable
data class EventMapColors(
    val live: Color,
    val soft: Color,
    val warning: Color,
    val info: Color,
    val danger: Color,
    val accent: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val mapBg: Color,
    val mapRoad: Color,
    val mapPark: Color,
    val mapWater: Color,
    val mapBuilding: Color,
    val brandGradient: List<Color>,
    val liveGradient: List<Color>,
)

val DayEventMapColors = EventMapColors(
    live = Live,
    soft = Soft,
    warning = Warning,
    info = Info,
    danger = Danger,
    accent = Coral500,
    textSecondary = DayTextSecondary,
    textTertiary = DayTextTertiary,
    mapBg = DayMapBg,
    mapRoad = DayMapRoad,
    mapPark = DayMapPark,
    mapWater = DayMapWater,
    mapBuilding = DayMapBuilding,
    brandGradient = BrandGradient,
    liveGradient = LiveGradient,
)

val NightEventMapColors = EventMapColors(
    live = Live,
    soft = Soft,
    warning = Warning,
    info = Info,
    danger = Danger,
    accent = Coral500,
    textSecondary = NightTextSecondary,
    textTertiary = NightTextTertiary,
    mapBg = NightMapBg,
    mapRoad = NightMapRoad,
    mapPark = NightMapPark,
    mapWater = NightMapWater,
    mapBuilding = NightMapBuilding,
    brandGradient = BrandGradient,
    liveGradient = LiveGradient,
)

val LocalEventMapColors = staticCompositionLocalOf { DayEventMapColors }
