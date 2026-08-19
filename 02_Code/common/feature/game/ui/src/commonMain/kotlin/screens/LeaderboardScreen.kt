package com.eventmap.game.ui.screens

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
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.game.ui.viewmodel.LeaderboardViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Лидерборд и достижения
//  LeaderboardScreen    — топ пользователей / друзей
//  AchievementsScreen   — все бейджи и прогресс
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Gold = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

private data class LeaderUser(
    val rank: Int,
    val name: String,
    val emoji: String,
    val xp: Int,
    val level: Int,
    val events: Int,
    val isMe: Boolean = false,
)

private val globalBoard = listOf(
    LeaderUser(1, "Тимур Ахметов", "😎", 18_420, 24, 142),
    LeaderUser(2, "Зарина Кадырова", "🥰", 16_870, 22, 128),
    LeaderUser(3, "Айгерим Нурова", "✨", 15_640, 21, 114),
    LeaderUser(4, "Данияр Асанов", "🤓", 14_200, 20, 107),
    LeaderUser(5, "Арайлым Сейт", "😄", 13_580, 19, 98),
    LeaderUser(6, "Азамат Темиржан", "🦁", 12_900, 18, 91, isMe = true),
    LeaderUser(7, "Баглан Оспанов", "🏆", 11_340, 17, 85),
    LeaderUser(8, "Камила Рза", "🎵", 10_870, 16, 79),
    LeaderUser(9, "Серик Алиев", "🎨", 9_450, 15, 71),
    LeaderUser(10, "Жанна Смаил", "🌿", 8_920, 14, 65),
)

private val friendsBoard = listOf(
    LeaderUser(1, "Тимур Ахметов", "😎", 18_420, 24, 142),
    LeaderUser(2, "Данияр Асанов", "🤓", 14_200, 20, 107),
    LeaderUser(3, "Азамат Темиржан", "🦁", 12_900, 18, 91, isMe = true),
    LeaderUser(4, "Арайлым Сейт", "😄", 13_580, 19, 98),
)

// ─────────────────────────────────────────────────────────────────────────────
//  LeaderboardScreen
// ─────────────────────────────────────────────────────────────────────────────

// Эмодзи-аватар: у бэка аватаров нет — детерминированно от имени
private val avatarEmojis = listOf("😎", "🥰", "✨", "🤓", "😄", "🦁", "🏆", "🎵", "🎨", "🌿", "🚀", "🔥")
private fun emojiFor(name: String): String {
    var h = 0
    for (c in name) h = h * 31 + c.code
    val idx = h % avatarEmojis.size
    return avatarEmojis[if (idx < 0) idx + avatarEmojis.size else idx]
}

class LeaderboardScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<LeaderboardViewModel>()
        val globalLive by vm.global.collectAsState()
        val friendsLive by vm.friends.collectAsState()
        var selectedTab by remember { mutableStateOf(0) }

        // Живые данные бэка; офлайн-фолбэк — демо (паттерн проекта)
        val liveBoard = (if (selectedTab == 0) globalLive else friendsLive).map { r ->
            LeaderUser(rank = r.rank, name = r.name, emoji = emojiFor(r.name), xp = r.xp, level = r.level, events = 0, isMe = r.isMe)
        }
        val board = liveBoard.ifEmpty { if (selectedTab == 0) globalBoard else friendsBoard }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Лидерборд", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.push(AchievementsScreen()) }.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("🏅 Бейджи", fontSize = 12.sp, color = NightTextSecondary) }
            }

            // Табы
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Глобальный", "Друзья").forEachIndexed { i, label ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) { Text(label, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            // Топ-3 пьедестал
            if (board.size >= 3) {
                PodiumSection(board[0], board[1], board[2])
            }

            // Остальные
            val rest = board.drop(3)
            if (rest.isNotEmpty()) {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    // Моя позиция, если не в топ-3
                    val meRank = board.find { it.isMe }
                    if (meRank != null && meRank.rank > 3) {
                        item {
                            LeaderRow(meRank, highlight = true)
                            Box(Modifier.fillMaxWidth().height(4.dp))
                        }
                    }
                    items(rest) { user -> LeaderRow(user, highlight = user.isMe) }
                }
            }
        }
    }
}

@Composable
private fun PodiumSection(first: LeaderUser, second: LeaderUser, third: LeaderUser) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(NightSurface).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
            PodiumItem(second, 2, 80.dp)
            PodiumItem(first, 1, 100.dp)
            PodiumItem(third, 3, 64.dp)
        }
    }
}

@Composable
private fun PodiumItem(user: LeaderUser, rank: Int, avatarSize: androidx.compose.ui.unit.Dp) {
    val medalEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
    val medalColor = when (rank) { 1 -> Gold; 2 -> Silver; else -> Bronze }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(avatarSize).clip(CircleShape)
                    .background(Brush.linearGradient(if (rank == 1) BrandGradient else listOf(medalColor.copy(alpha = 0.30f), medalColor.copy(alpha = 0.30f))))
                    .border(2.dp, medalColor.copy(alpha = 0.70f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(user.emoji, fontSize = (avatarSize.value * 0.45f).sp) }
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(NightSurface).border(1.dp, NightBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(medalEmoji, fontSize = 12.sp) }
        }
        Text(user.name.split(" ").first(), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(xpLabel(user.xp), fontSize = 10.sp, color = medalColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LeaderRow(user: LeaderUser, highlight: Boolean) {
    val bg = if (highlight) Violet600.copy(alpha = 0.12f) else Color.Transparent
    val border = if (highlight) Violet600.copy(alpha = 0.30f) else Color.Transparent

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Номер
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text("${user.rank}", fontSize = 14.sp, color = if (highlight) Violet400 else NightTextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (highlight) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(user.emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (user.isMe) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.20f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Вы", fontSize = 9.sp, color = Violet400)
                    }
                }
            }
            Text(
                if (user.events > 0) "Ур. ${user.level} · ${user.events} событий" else "Ур. ${user.level}",
                fontSize = 10.sp, color = NightTextSecondary,
            )
        }
        Text(xpLabel(user.xp), fontSize = 12.sp, color = if (highlight) Violet400 else NightTextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

// «12K XP» для больших чисел, «340 XP» для маленьких (реальные значения с бэка скромнее демо)
private fun xpLabel(xp: Int): String = if (xp >= 1000) "${xp / 1000}K XP" else "$xp XP"

// ─────────────────────────────────────────────────────────────────────────────
//  AchievementsScreen — все бейджи
// ─────────────────────────────────────────────────────────────────────────────

private data class BadgeDef(
    val emoji: String,
    val name: String,
    val desc: String,
    val unlocked: Boolean,
    val progress: Float = 1f,
)

private val allBadges = listOf(
    BadgeDef("🎵", "Меломан", "Посетил 5 музыкальных событий", true),
    BadgeDef("🏆", "Коллекционер", "Посетил 10 событий", true),
    BadgeDef("🌟", "Первооткрыватель", "Первым отметился на новом событии", true),
    BadgeDef("🔴", "LIVE-зритель", "Смотрел 3 Live-трансляции", true),
    BadgeDef("👑", "Топ-10", "Попал в топ-10 лидерборда", false, 0.6f),
    BadgeDef("📸", "Фотограф", "Загрузил 20 фото с событий", false, 0.4f),
    BadgeDef("🎯", "Квест-мастер", "Выполнил 5 квестов", false, 0.33f),
    BadgeDef("🤝", "Социальный", "Пригласил 10 друзей", false, 0.2f),
    BadgeDef("🌍", "Путешественник", "Посетил события в 3 городах", false, 0.33f),
    BadgeDef("🎉", "Организатор", "Создал своё первое событие", false, 0f),
    BadgeDef("💎", "VIP", "Потратил 50 000 ₸ на билеты", false, 0.18f),
    BadgeDef("⚡", "Марафонец", "7 дней подряд активен в приложении", false, 0.43f),
)

class AchievementsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val unlocked = allBadges.count { it.unlocked }

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
                Column(Modifier.weight(1f)) {
                    Text("Достижения", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("$unlocked из ${allBadges.size} разблокировано", fontSize = 12.sp, color = NightTextSecondary)
                }
            }

            // XP и уровень
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(NightSurface).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Gold.copy(alpha = 0.30f), Gold.copy(alpha = 0.10f))))
                            .border(2.dp, Gold.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("18", fontSize = 18.sp, color = Gold, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("12 900 XP", fontSize = 14.sp, color = Violet400, fontWeight = FontWeight.ExtraBold)
                            Text("Ур. 18 → 19", fontSize = 11.sp, color = NightTextSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.10f))) {
                            Box(Modifier.fillMaxWidth(0.72f).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient)))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Нужно ещё 1 600 XP", fontSize = 10.sp, color = NightTextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(start = 20.dp, bottom = 10.dp)) {
                Text("ВСЕ БЕЙДЖИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allBadges.chunked(1)) { chunk ->
                    chunk.forEach { badge -> BadgeRow(badge) }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: BadgeDef) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (badge.unlocked) NightSurface else NightSurface.copy(alpha = 0.5f))
            .border(1.dp, if (badge.unlocked) Gold.copy(alpha = 0.20f) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (badge.unlocked) Brush.linearGradient(listOf(Gold.copy(alpha = 0.20f), Gold.copy(alpha = 0.10f))) else Brush.linearGradient(listOf(NightBackground, NightBackground)))
                .border(2.dp, if (badge.unlocked) Gold.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.07f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge.emoji, fontSize = 20.sp, color = if (badge.unlocked) Color.Unspecified else Color.Gray)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(badge.name, fontSize = 13.sp, color = if (badge.unlocked) Color.White else NightTextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(badge.desc, fontSize = 11.sp, color = NightTextTertiary)
            if (!badge.unlocked && badge.progress > 0f) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(badge.progress).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.60f)))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (badge.unlocked) {
            Text("✓", fontSize = 18.sp, color = Gold)
        } else if (badge.progress > 0f) {
            Text("${(badge.progress * 100).toInt()}%", fontSize = 11.sp, color = NightTextTertiary)
        } else {
            Text("🔒", fontSize = 16.sp)
        }
    }
}
