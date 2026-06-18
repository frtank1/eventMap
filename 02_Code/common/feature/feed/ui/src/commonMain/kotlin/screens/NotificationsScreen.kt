package com.eventmap.feed.ui.screens

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

// =====================================================================
//  Уведомления
//  NotificationsScreen     — центр уведомлений с табами
//  NotificationDetailScreen — детальное уведомление
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Live = Color(0xFFFF4757)
private val Coral500 = Color(0xFFFF5C8A)

private enum class NotifType { SOCIAL, EVENT, SYSTEM, PROMO }

private data class NotifItem(
    val id: String,
    val type: NotifType,
    val emoji: String,
    val title: String,
    val body: String,
    val time: String,
    val isRead: Boolean = false,
    val actionLabel: String? = null,
)

private val sampleNotifs = listOf(
    NotifItem("n1", NotifType.SOCIAL, "😎", "Тимур Ахметов", "подписался на вас", "2 мин", false, null),
    NotifItem("n2", NotifType.EVENT, "🎵", "Jazz Квартал · Алатау", "Событие начинается через 1 час! Не забудьте взять с собой билет.", "15 мин", false, "Мой билет"),
    NotifItem("n3", NotifType.SOCIAL, "🥰", "Зарина Кадырова", "отметила вас на фото с маркета", "1 ч", false, "Посмотреть"),
    NotifItem("n4", NotifType.EVENT, "🎨", "Арт-маркет «Апрель»", "Осталось 5 мест — успейте купить билет!", "2 ч", true, "Купить"),
    NotifItem("n5", NotifType.SOCIAL, "🤓", "Данияр Асанов", "оценил ваш отзыв о стендапе", "3 ч", true, null),
    NotifItem("n6", NotifType.SYSTEM, "⚡", "EventMap", "Вы набрали 500 XP и перешли на уровень 8!", "5 ч", true, "Мои достижения"),
    NotifItem("n7", NotifType.PROMO, "🎁", "Специальное предложение", "Получите скидку 20% на следующий билет по промокоду SPRING26", "Вчера", true, "Активировать"),
    NotifItem("n8", NotifType.EVENT, "🎤", "Comedy Club KZ", "Новый сезон стартует 20 июня! Раннее бронирование открыто.", "Вчера", true, "Подробнее"),
    NotifItem("n9", NotifType.SOCIAL, "😄", "Арайлым", "начала следить за вами", "2 дня", true, null),
    NotifItem("n10", NotifType.SYSTEM, "🏆", "EventMap", "Вы получили бейдж «Коллекционер» за посещение 10 событий!", "3 дня", true, "Открыть"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationsScreen
// ─────────────────────────────────────────────────────────────────────────────

class NotificationsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Все", "Соцсеть", "События", "Система")

        val filtered = when (selectedTab) {
            1 -> sampleNotifs.filter { it.type == NotifType.SOCIAL }
            2 -> sampleNotifs.filter { it.type == NotifType.EVENT }
            3 -> sampleNotifs.filter { it.type == NotifType.SYSTEM || it.type == NotifType.PROMO }
            else -> sampleNotifs
        }
        val unreadCount = sampleNotifs.count { !it.isRead }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 20.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Уведомления", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                if (unreadCount > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.20f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("$unreadCount новых", fontSize = 12.sp, color = Violet400) }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple {}.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("Прочитать все", fontSize = 11.sp, color = NightTextSecondary) }
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
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text(t, fontSize = 12.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(filtered) { notif ->
                    NotifRow(notif) { nav.push(NotificationDetailScreen(notif.id)) }
                }
            }
        }
    }
}

@Composable
private fun NotifRow(notif: NotifItem, onClick: () -> Unit) {
    val iconBg = when (notif.type) {
        NotifType.SOCIAL -> Brush.linearGradient(BrandGradient)
        NotifType.EVENT -> Brush.linearGradient(listOf(Violet600.copy(alpha = 0.40f), Violet600.copy(alpha = 0.40f)))
        NotifType.SYSTEM -> Brush.linearGradient(listOf(Soft.copy(alpha = 0.30f), Soft.copy(alpha = 0.30f)))
        NotifType.PROMO -> Brush.linearGradient(listOf(Coral500.copy(alpha = 0.30f), Coral500.copy(alpha = 0.30f)))
    }

    Row(
        Modifier.fillMaxWidth()
            .background(if (!notif.isRead) Violet600.copy(alpha = 0.05f) else Color.Transparent)
            .clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center,
            ) { Text(notif.emoji, fontSize = 20.sp) }
            if (!notif.isRead) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Live).border(2.dp, NightBackground, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(notif.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(notif.time, fontSize = 10.sp, color = NightTextTertiary)
            }
            Spacer(Modifier.height(3.dp))
            Text(notif.body, fontSize = 12.sp, color = NightTextSecondary, lineHeight = 17.sp, maxLines = 2)
            if (notif.actionLabel != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Violet600.copy(alpha = 0.18f))
                        .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) { Text(notif.actionLabel, fontSize = 11.sp, color = Violet400, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    // Разделитель
    Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 72.dp, end = 16.dp).background(Color.White.copy(alpha = 0.04f)))
}

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationDetailScreen — детальное уведомление
// ─────────────────────────────────────────────────────────────────────────────

data class NotificationDetailScreen(val notifId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val notif = sampleNotifs.find { it.id == notifId } ?: sampleNotifs.first()

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Уведомление", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                // Иконка
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text(notif.emoji, fontSize = 36.sp) }
                }

                // Заголовок
                Text(notif.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(notif.time, fontSize = 12.sp, color = NightTextTertiary)
                Spacer(Modifier.height(16.dp))

                // Тело
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                ) {
                    Text(
                        when (notif.type) {
                            NotifType.EVENT -> "${notif.body}\n\nПожалуйста, убедитесь что ваш билет доступен офлайн в разделе «Мои билеты»."
                            NotifType.SYSTEM -> "${notif.body}\n\nПродолжайте посещать события чтобы разблокировать новые достижения и повышать уровень."
                            NotifType.PROMO -> "${notif.body}\n\nПредложение действительно до 20 июня 2026 года. Применяется к любому событию стоимостью от 1 000 ₸."
                            else -> notif.body
                        },
                        fontSize = 14.sp, color = Color.White, lineHeight = 22.sp,
                    )
                }

                if (notif.actionLabel != null) {
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.pop() },
                        contentAlignment = Alignment.Center,
                    ) { Text(notif.actionLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("Закрыть", color = NightTextSecondary, fontSize = 14.sp) }
            }
        }
    }
}
