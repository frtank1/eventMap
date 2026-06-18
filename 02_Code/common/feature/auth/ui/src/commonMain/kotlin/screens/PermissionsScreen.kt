package com.eventmap.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  B1/B2/B3 — Разрешения (3 шага: геолокация / камера / уведомления)
//  Шаблон: эмодзи в концентрических кольцах, заголовок, объяснение, CTA
// =====================================================================

private data class PermissionPage(
    val emoji: String,
    val title: String,
    val body: String,
    val ctaLabel: String,
)

private val permissionPages = listOf(
    PermissionPage(
        emoji = "📍",
        title = "Где ты?",
        body = "Показываем события рядом и активируем «Я тоже здесь». Гео — только пока приложение открыто.",
        ctaLabel = "Разрешить геолокацию",
    ),
    PermissionPage(
        emoji = "📷",
        title = "Камера для рилсов",
        body = "Снимай 30-секундные live-видео с событий и делись моментами прямо сейчас.",
        ctaLabel = "Разрешить камеру",
    ),
    PermissionPage(
        emoji = "🔔",
        title = "Не пропускай движ",
        body = "Пришлём напоминание о событии и оповещение, когда рядом что-то началось.",
        ctaLabel = "Разрешить уведомления",
    ),
)

class PermissionsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val homeProvider = remember { getKoin().get<HomeScreenProvider>() }
        var step by remember { mutableStateOf(0) }

        val page = permissionPages[step]

        fun advance() {
            if (step < permissionPages.lastIndex) step++
            else nav.replaceAll(homeProvider.home())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            // Прогресс-точки
            PermissionDots(count = permissionPages.size, current = step)

            Spacer(Modifier.height(56.dp))

            // Концентрические кольца с эмодзи
            PermissionRings(page.emoji)

            Spacer(Modifier.height(36.dp))

            Text(
                page.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                page.body,
                fontSize = 14.sp,
                color = NightTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.weight(1f))

            // Primary CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { advance() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    page.ctaLabel,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            // «Не сейчас» — ghost
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickableNoRipple { advance() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Не сейчас",
                    color = NightTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun PermissionRings(emoji: String) {
    Box(contentAlignment = Alignment.Center) {
        // Внешнее кольцо
        Box(
            Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Violet600.copy(alpha = 0.08f), Color.Transparent)),
                ),
        )
        // Среднее
        Box(
            Modifier
                .size(174.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f)),
        )
        // Внутренний
        Box(
            Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 48.sp)
        }
    }
}

@Composable
private fun PermissionDots(count: Int, current: Int) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 24.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (i == current) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.20f))),
                    ),
            )
        }
    }
}
