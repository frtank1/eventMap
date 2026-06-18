package com.eventmap.profile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlin.math.roundToInt

// =====================================================================
//  UserActivityScreen  — история событий + статистика
//  EventReviewScreen   — отзывы о событии / написать отзыв
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Gold = Color(0xFFFFD700)
private val Coral500 = Color(0xFFFF5C8A)

private data class VisitedEvent(
    val id: String,
    val emoji: String,
    val title: String,
    val venue: String,
    val date: String,
    val xpEarned: Int,
    val hasReview: Boolean = false,
    val rating: Int = 0,
)

private val visitedHistory = listOf(
    VisitedEvent("v1", "🎵", "Jazz Квартал · Весенний сезон", "Алатау Бар", "Пт, 7 июн", 120, true, 5),
    VisitedEvent("v2", "🎨", "Арт-маркет «Апрель»", "TSUM Almaty", "Сб, 8 июн", 80, false),
    VisitedEvent("v3", "🎤", "Стендап: Новые голоса", "Forte Hall", "Пт, 31 мая", 100, true, 4),
    VisitedEvent("v4", "🛍", "Craft Маркет «Весна»", "Mega Center", "Сб, 25 мая", 60, false),
    VisitedEvent("v5", "🎬", "Кинопоказ: Казахское кино", "Арман", "Вт, 20 мая", 80, true, 5),
    VisitedEvent("v6", "🌿", "Эко-фестиваль «Зелёный Алматы»", "Парк Горького", "Сб, 17 мая", 140, false),
    VisitedEvent("v7", "📚", "TED×Almaty Youth", "KIMEP University", "Пт, 10 мая", 90, true, 4),
)

// ─────────────────────────────────────────────────────────────────────────────
//  UserActivityScreen — история посещений
// ─────────────────────────────────────────────────────────────────────────────

class UserActivityScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("История", "Статистика")

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Моя активность", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            }

            // Табы
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) { Text(t, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            if (selectedTab == 0) {
                HistoryTab(nav)
            } else {
                StatsTab()
            }
        }
    }
}

@Composable
private fun HistoryTab(nav: cafe.adriel.voyager.navigator.Navigator) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        // Всего посещений
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniStat(Modifier.weight(1f), "🏟", "${visitedHistory.size}", "событий")
                MiniStat(Modifier.weight(1f), "⭐", "${visitedHistory.sumOf { it.xpEarned }}", "XP заработано")
                MiniStat(Modifier.weight(1f), "📝", "${visitedHistory.count { it.hasReview }}", "отзывов")
            }
        }
        items(visitedHistory) { event ->
            VisitRow(event) {
                nav.push(EventReviewScreen(event.id, event.title, event.emoji))
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, emoji: String, value: String, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 9.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VisitRow(event: VisitedEvent, onReviewClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) { Text(event.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.venue, fontSize = 11.sp, color = NightTextSecondary)
                Text(" · ", fontSize = 11.sp, color = NightTextTertiary)
                Text(event.date, fontSize = 11.sp, color = NightTextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // XP бейдж
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("+${event.xpEarned} XP", fontSize = 10.sp, color = Violet400, fontWeight = FontWeight.SemiBold) }
                // Отзыв
                if (event.hasReview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(event.rating) { Text("★", fontSize = 10.sp, color = Gold) }
                        repeat(5 - event.rating) { Text("★", fontSize = 10.sp, color = NightTextTertiary) }
                    }
                } else {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickableNoRipple(onReviewClick).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("+ Написать отзыв", fontSize = 10.sp, color = NightTextSecondary) }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 74.dp, end = 16.dp).background(Color.White.copy(alpha = 0.04f)))
}

@Composable
private fun StatsTab() {
    val categoryStats = listOf(
        "🎵 Музыка" to 0.42f, "🎨 Арт" to 0.22f, "🛍 Маркет" to 0.14f,
        "🎤 Стендап" to 0.10f, "🎬 Кино" to 0.08f, "🌿 Природа" to 0.04f,
    )
    val monthStats = listOf("Янв" to 0, "Фев" to 1, "Мар" to 2, "Апр" to 3, "Май" to 4, "Июн" to 2)

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Общая активность
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp)) {
                Column {
                    Text("АКТИВНОСТЬ ПО МЕСЯЦАМ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        monthStats.forEach { (month, count) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (count > 0) {
                                    Box(
                                        Modifier.width(24.dp).height((count * 14).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(Brush.linearGradient(BrandGradient)),
                                    )
                                } else {
                                    Box(Modifier.width(24.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(NightBackground))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(month, fontSize = 9.sp, color = NightTextTertiary)
                            }
                        }
                    }
                }
            }
        }
        item {
            // Категории
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ЛЮБИМЫЕ КАТЕГОРИИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(2.dp))
                    categoryStats.forEach { (label, frac) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.width(100.dp))
                            Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient)))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${(frac * 100).toInt()}%", fontSize = 10.sp, color = NightTextSecondary, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        item {
            // Рекорды
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("РЕКОРДЫ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(2.dp))
                    RecordRow("🔥", "Серия подряд", "5 событий за неделю")
                    RecordRow("📍", "Самый дальний", "Astana Jazz Festival")
                    RecordRow("⏰", "Самый ранний", "Эко-забег 07:00")
                    RecordRow("🌙", "Самый поздний", "Afterparty 03:30")
                }
            }
        }
    }
}

@Composable
private fun RecordRow(emoji: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EventReviewScreen — отзывы о событии + написать отзыв
// ─────────────────────────────────────────────────────────────────────────────

private data class Review(
    val author: String,
    val avatarEmoji: String,
    val rating: Int,
    val text: String,
    val date: String,
    val helpful: Int,
)

private val sampleReviews = listOf(
    Review("Тимур А.", "😎", 5, "Невероятная атмосфера! Jazz квартал каждый раз удивляет новыми исполнителями. Обязательно приду снова.", "7 июн", 14),
    Review("Зарина К.", "🥰", 5, "Лучший концерт этой весны. Звук, освещение, публика — всё на высшем уровне. 10/10", "7 июн", 8),
    Review("Данияр А.", "🤓", 4, "Хорошая программа, но начало задержали на 20 минут. В остальном — отлично.", "6 июн", 5),
    Review("Арайлым С.", "😄", 5, "Была с друзьями — провели великолепный вечер! Заодно познакомились с новыми людьми прямо там.", "6 июн", 3),
)

data class EventReviewScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var myRating by remember { mutableStateOf(0) }
        var reviewText by remember { mutableStateOf("") }
        var submitted by remember { mutableStateOf(false) }

        val avgRating = sampleReviews.map { it.rating }.average()
        // KMP: String.format недоступен в commonMain — форматируем до 1 знака вручную
        val avgRatingText = (avgRating * 10).roundToInt().let { "${it / 10}.${it % 10}" }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text(eventEmoji, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Отзывы", fontSize = 14.sp, color = NightTextSecondary)
                    Text(eventTitle, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Общий рейтинг
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                            Text(avgRatingText, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Row { repeat(5) { i -> Text("★", fontSize = 14.sp, color = if (i < avgRating) Gold else NightTextTertiary) } }
                            Spacer(Modifier.height(4.dp))
                            Text("${sampleReviews.size} отзыва", fontSize = 10.sp, color = NightTextSecondary)
                        }
                        Spacer(Modifier.width(16.dp))
                        // Гистограмма рейтингов
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            (5 downTo 1).forEach { star ->
                                val count = sampleReviews.count { it.rating == star }
                                val frac = count.toFloat() / sampleReviews.size
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$star", fontSize = 10.sp, color = NightTextSecondary, modifier = Modifier.width(12.dp))
                                    Text("★", fontSize = 9.sp, color = Gold)
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
                                        if (frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Gold.copy(alpha = 0.70f)))
                                    }
                                }
                            }
                        }
                    }
                }

                // Написать отзыв (если ещё не отправлен)
                if (!submitted) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                        ) {
                            Text("Ваш отзыв", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            // Звёзды
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..5).forEach { s ->
                                    Text(
                                        "★", fontSize = 28.sp,
                                        color = if (s <= myRating) Gold else NightTextTertiary,
                                        modifier = Modifier.clickableNoRipple { myRating = s },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Текстовое поле-заглушка
                            Box(
                                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                            ) {
                                Text(
                                    reviewText.ifEmpty { "Расскажите о своих впечатлениях..." },
                                    fontSize = 13.sp, color = if (reviewText.isEmpty()) NightTextTertiary else Color.White,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (myRating > 0) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                    .border(1.dp, if (myRating == 0) Color.White.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickableNoRipple { if (myRating > 0) submitted = true },
                                contentAlignment = Alignment.Center,
                            ) { Text("Отправить", color = if (myRating > 0) Color.White else NightTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                } else {
                    item {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Soft.copy(alpha = 0.12f))
                                .border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(16.dp)).padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("✓", fontSize = 18.sp, color = Soft, fontWeight = FontWeight.ExtraBold)
                                Text("Отзыв опубликован! +50 XP", fontSize = 14.sp, color = Soft, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Список отзывов
                item { Text("ВСЕ ОТЗЫВЫ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 4.dp)) }
                items(sampleReviews) { review -> ReviewCard(review) }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text(review.avatarEmoji, fontSize = 16.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(review.author, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Row {
                    repeat(review.rating) { Text("★", fontSize = 11.sp, color = Gold) }
                    repeat(5 - review.rating) { Text("★", fontSize = 11.sp, color = NightTextTertiary) }
                }
            }
            Text(review.date, fontSize = 10.sp, color = NightTextTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Text(review.text, fontSize = 13.sp, color = NightTextSecondary, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👍", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text("${review.helpful} полезно", fontSize = 11.sp, color = NightTextTertiary)
        }
    }
}
