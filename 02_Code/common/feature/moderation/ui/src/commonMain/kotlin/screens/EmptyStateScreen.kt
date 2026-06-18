package com.eventmap.moderation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  K1–K4 · Пустые состояния / ошибки
//  Универсальный компонент + 4 пресета
// =====================================================================

data class EmptyStateConfig(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val ghostLabel: String? = null,
)

val EmptyStateOffline = EmptyStateConfig(
    emoji = "📡",
    title = "Нет соединения",
    subtitle = "Показываем сохранённые данные. Подключись к сети, чтобы увидеть актуальное.",
    primaryLabel = "Повторить",
    ghostLabel = "Офлайн-режим",
)

val EmptyStateNoEvents = EmptyStateConfig(
    emoji = "🗺",
    title = "Событий нет рядом",
    subtitle = "В этом радиусе пусто. Расширь поиск или создай своё событие.",
    primaryLabel = "Расширить радиус",
    ghostLabel = "Создать событие",
)

val EmptyStateNoFeed = EmptyStateConfig(
    emoji = "📭",
    title = "Лента пуста",
    subtitle = "Подпишись на организаторов или жанры, чтобы видеть актуальные события.",
    primaryLabel = "Выбрать интересы",
)

val EmptyStateServerError = EmptyStateConfig(
    emoji = "🛠",
    title = "Что-то пошло не так",
    subtitle = "Сервер временно недоступен. Мы уже знаем об этом и чиним.",
    primaryLabel = "Повторить",
)

@Composable
fun EmptyState(
    config: EmptyStateConfig,
    modifier: Modifier = Modifier,
    onPrimary: () -> Unit = {},
    onGhost: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().background(NightBackground).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(config.emoji, fontSize = 64.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(config.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(config.subtitle, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(32.dp))

        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(BrandGradient)).clickableNoRipple(onPrimary),
            contentAlignment = Alignment.Center,
        ) { Text(config.primaryLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }

        if (config.ghostLabel != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple(onGhost),
                contentAlignment = Alignment.Center,
            ) { Text(config.ghostLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp) }
        }
    }
}

// Convenience previews для каждого типа
@Composable fun OfflineScreen(onRetry: () -> Unit = {}) =
    EmptyState(EmptyStateOffline, onPrimary = onRetry)

@Composable fun NoEventsScreen(onExpand: () -> Unit = {}, onCreate: () -> Unit = {}) =
    EmptyState(EmptyStateNoEvents, onPrimary = onExpand, onGhost = onCreate)

@Composable fun NoFeedScreen(onPickInterests: () -> Unit = {}) =
    EmptyState(EmptyStateNoFeed, onPrimary = onPickInterests)

@Composable fun ServerErrorScreen(onRetry: () -> Unit = {}) =
    EmptyState(EmptyStateServerError, onPrimary = onRetry)
