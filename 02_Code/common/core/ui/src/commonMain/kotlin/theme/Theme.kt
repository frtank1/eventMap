package com.eventmap.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Violet400,
    secondary = Coral500,
    onSecondary = Color.White,
    background = DayBackground,
    onBackground = DayTextPrimary,
    surface = DaySurface,
    onSurface = DayTextPrimary,
    surfaceVariant = Color(0xFFF1EFE8),
    onSurfaceVariant = DayTextSecondary,
    error = Danger,
    onError = Color.White,
    outline = Color(0xFFE5E5E5),
)

private val DarkColors = darkColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Violet700,
    secondary = Coral500,
    onSecondary = Color.White,
    background = NightBackground,
    onBackground = NightTextPrimary,
    surface = NightSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = Color(0x12FFFFFF),
    onSurfaceVariant = NightTextSecondary,
    error = Live,
    onError = Color.White,
    outline = Color(0xFF2A3A4D),
)

/**
 * Корневая тема EventMap. Помимо Material ColorScheme прокидывает кастомные
 * токены: [LocalSpacing] и [LocalEventMapColors] (семантика/карта).
 *
 * Доступ из экранов:
 *   MaterialTheme.colorScheme.primary
 *   EventMapTheme.spacing.lg
 *   EventMapTheme.colors.live
 */
@Composable
fun EventMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extraColors = if (darkTheme) NightEventMapColors else DayEventMapColors

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalEventMapColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = eventMapTypography(),
            shapes = replyShapes,
            content = content,
        )
    }
}

/** Удобный доступ к кастомным токенам: EventMapTheme.spacing / EventMapTheme.colors. */
object EventMapTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
    val colors: EventMapColors
        @Composable get() = LocalEventMapColors.current
}

/** Совместимость со старым ui-kit/example. Светлая тема по умолчанию. */
@Composable
fun NisTheme(content: @Composable () -> Unit) {
    EventMapTheme(darkTheme = false, content = content)
}
