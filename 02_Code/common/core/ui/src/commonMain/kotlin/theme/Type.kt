package com.eventmap.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventmap.core.ui.resources.Inter_Black
import com.eventmap.core.ui.resources.Inter_Bold
import com.eventmap.core.ui.resources.Inter_Light
import com.eventmap.core.ui.resources.Inter_Medium
import com.eventmap.core.ui.resources.Inter_Regular
import com.eventmap.core.ui.resources.Inter_Thin
import com.eventmap.core.ui.resources.Res
import org.jetbrains.compose.resources.Font

// Шрифт дизайн-системы — Manrope (DESIGN_TOKENS §2).
// TODO: положить Manrope-*.ttf в commonMain/composeResources/font и переключить
//       Font(...) ниже на Manrope. Сейчас как fallback используется bundled Inter.
@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Inter_Thin, FontWeight.Thin),
    Font(Res.font.Inter_Light, FontWeight.Light),
    Font(Res.font.Inter_Regular, FontWeight.Normal),
    Font(Res.font.Inter_Medium, FontWeight.Medium),
    Font(Res.font.Inter_Bold, FontWeight.Bold),
    Font(Res.font.Inter_Black, FontWeight.Black),
)

// Совместимость со старым ui-kit (Texts.kt вызывает interFontFamily()).
@Composable
fun interFontFamily(): FontFamily = appFontFamily()

// Типографика Aurora Violet, замаплена на Material3 TextStyle-роли.
@Composable
fun eventMapTypography(): Typography {
    val f = appFontFamily()
    return Typography(
        // DisplayLarge — hero онбординга (32/700/40)
        displayLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
        // DisplayMedium — заголовки экранов (24/600/32)
        displayMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        // TitleLarge — заголовки секций (18/600/24)
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        // TitleMedium — карточки (16/600/22)
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        // BodyLarge — описание (15/400/22)
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        // BodyMedium — подписи (13/400/18)
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
        // LabelLarge — кнопки (13/500/16)
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
        // LabelMedium — пиллы/бейджи (11/500/14)
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
        // LabelSmall — LIVE/СКОРО/ИДЁТ (9/500/12)
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 12.sp),
    )
}

// Дефолт без шрифт-ресурса (для не-@Composable контекстов/превью fallback).
val Typography = Typography()
