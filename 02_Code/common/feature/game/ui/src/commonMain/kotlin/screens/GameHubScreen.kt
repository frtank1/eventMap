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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.game.ui.models.BadgeItem
import com.eventmap.game.ui.models.GameState
import com.eventmap.game.ui.models.LeaderboardEntry
import com.eventmap.game.ui.models.MissionItem
import com.eventmap.game.ui.models.SeasonPassItem
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.LiveGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.koin.koinScreenModel
import com.eventmap.game.ui.viewmodel.GameHubViewModel

// =====================================================================
//  R1 · Игровой хаб (уровень, значки, сезонный пасс, миссии, лидерборд)
// =====================================================================

class GameHubScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        // Данные с бэка (/game/*); при недоступном бэке — демо (паттерн MapViewModel)
        val vm = koinScreenModel<GameHubViewModel>()
        val state = vm.state.collectAsState().value ?: return

        LazyColumn(
            Modifier.fillMaxSize().background(NightBackground),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // ── Шапка ────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Игровой хаб", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Violet600.copy(alpha = 0.18f))
                            .border(1.dp, Violet400.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Обёртка года →", fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Карточка уровня ───────────────────────────────────────
            item { LevelCard(state) }

            // ── Значки ────────────────────────────────────────────────
            item {
                SectionHeader("ЗНАЧКИ-АЧИВКИ")
                BadgesRow(state.badges)
            }

            // ── Сезонный пасс ─────────────────────────────────────────
            item {
                SectionHeader("СЕЗОННЫЙ ПАСС · ${state.seasonName}")
                SeasonPassTrack(state)
            }

            // ── Миссии ────────────────────────────────────────────────
            item { SectionHeader("МИССИИ") }
            items(state.missions, key = { it.id }) { m -> MissionRow(m) }

            // ── Лидерборд ──────────────────────────────────────────────
            item {
                SectionHeader("ЛИДЕРБОРД РАЙОНОВ · неделя")
                state.leaderboard.forEach { e -> LeaderboardRow(e) }
            }

            // ── Топ игроков + капсулы времени (R2/R3) ─────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        emoji = "👑",
                        title = "Топ игроков",
                        subtitle = "Рейтинг по XP",
                    ) { nav.push(LeaderboardScreen()) }
                    HubActionCard(
                        modifier = Modifier.weight(1f),
                        emoji = "💊",
                        title = "Капсула времени",
                        subtitle = "Послание месту",
                    ) { nav.push(TimeCapsuleScreen()) }
                }
            }
        }
    }
}

@Composable
private fun HubActionCard(
    modifier: Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurface)
            .border(1.dp, Violet600.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .clickableNoRipple(onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 11.sp, color = NightTextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Карточка уровня (R1)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LevelCard(s: GameState) {
    val xpProgress = s.xp.toFloat() / s.xpNextLevel.coerceAtLeast(1)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1040), Color(0xFF0E1E33))))
            .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Аватар с уровнем
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${s.level}", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Уровень ${s.level}", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(s.title, fontSize = 13.sp, color = Violet400, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                Text("${s.xp} / ${s.xpNextLevel} XP", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // XP-бар
        LinearProgressIndicator(
            progress = { xpProgress },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
            color = Violet600,
            trackColor = Color.White.copy(alpha = 0.10f),
            strokeCap = StrokeCap.Round,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.xp} XP", fontSize = 10.sp, color = NightTextTertiary)
            Text("${s.xpNextLevel - s.xp} XP до след. уровня", fontSize = 10.sp, color = NightTextTertiary)
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Значки
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgesRow(badges: List<BadgeItem>) {
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(badges, key = { it.id }) { b ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(
                        if (b.unlocked) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(NightSurface, NightSurface)),
                    ).then(if (!b.unlocked) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (b.unlocked) b.emoji else "🔒", fontSize = 24.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    b.title,
                    fontSize = 10.sp,
                    color = if (b.unlocked) Color.White else NightTextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Сезонный пасс — горизонтальный трек
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeasonPassTrack(s: GameState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurface)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${s.seasonProgress} / ${s.seasonTotal} квестов", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Soft.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("🎁 Лут от бизнеса", fontSize = 11.sp, color = Soft, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.height(12.dp))
        // Трек
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            s.seasonPasses.forEachIndexed { i, sp ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(
                            if (sp.unlocked) Brush.linearGradient(BrandGradient)
                            else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
                        ).border(if (!sp.unlocked) 1.dp else 0.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(sp.emoji, fontSize = 18.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(sp.title, fontSize = 9.sp, color = if (sp.unlocked) Color.White else NightTextTertiary, textAlign = TextAlign.Center)
                }
                if (i < s.seasonPasses.lastIndex) {
                    Box(Modifier.height(2.dp).weight(1f).background(if (s.seasonPasses[i + 1].unlocked) Violet600.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка миссии
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MissionRow(m: MissionItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (m.done) Soft.copy(alpha = 0.07f) else NightSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                if (m.done) Soft.copy(alpha = 0.18f)
                else if (m.isCoOp) Info.copy(alpha = 0.18f)
                else Violet600.copy(alpha = 0.18f),
            ),
            contentAlignment = Alignment.Center,
        ) { Text(m.emoji, fontSize = 20.sp) }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, fontSize = 13.sp, color = if (m.done) NightTextSecondary else Color.White, fontWeight = FontWeight.SemiBold)
                if (m.isCoOp) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Info.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("2×XP", fontSize = 9.sp, color = Info, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (m.progress > 0f && !m.done) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { m.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
                    color = Violet600,
                    trackColor = Color.White.copy(alpha = 0.08f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text("+${m.xpReward} XP", fontSize = 11.sp, color = Violet400, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Лидерборд районов
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(e: LeaderboardEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (e.isCurrentUser) Violet600.copy(alpha = 0.15f) else NightSurface)
            .then(if (e.isCurrentUser) Modifier.border(1.dp, Violet600.copy(alpha = 0.40f), RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Место
        Text(
            when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${e.rank}" },
            fontSize = if (e.rank <= 3) 20.sp else 14.sp,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(32.dp),
        )
        Text(e.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(e.districtName, fontSize = 14.sp, color = Color.White, fontWeight = if (e.isCurrentUser) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text("${e.score} оч.", fontSize = 13.sp, color = if (e.isCurrentUser) Violet400 else NightTextSecondary, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Вспомогательные
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}


// ─────────────────────────────────────────────────────────────────────────────
//  Демо-данные
// ─────────────────────────────────────────────────────────────────────────────

internal fun sampleGameState() = GameState(
    level = 7, title = "Городской исследователь", xp = 2340, xpNextLevel = 3000,
    badges = listOf(
        BadgeItem("b1", "🌆", "Первопроходец", "Открыл 5 районов", true),
        BadgeItem("b2", "🎵", "Меломан", "10 концертов", true),
        BadgeItem("b3", "⚽", "Фанат", "5 спорт-событий", true),
        BadgeItem("b4", "🏆", "Квестер", "Пройди 3 квеста", false),
        BadgeItem("b5", "🌙", "Полуночник", "10 ночных событий", false),
    ),
    seasonName = "Лето 2026",
    seasonProgress = 3, seasonTotal = 7,
    seasonPasses = listOf(
        SeasonPassItem(0, "Кофе", "☕", true),
        SeasonPassItem(1, "Мерч", "👕", true),
        SeasonPassItem(2, "Концерт", "🎫", true),
        SeasonPassItem(3, "Ужин", "🍽", false),
        SeasonPassItem(4, "Экспо", "🏛", false),
    ),
    missions = listOf(
        MissionItem("m1", "🌆", "Открой новый район", 200, progress = 0f),
        MissionItem("m2", "🎵", "Сходи на концерт вместе с другом", 400, isCoOp = true, progress = 0.5f),
        MissionItem("m3", "🧘", "Попробуй новый жанр — Йога", 150, progress = 0f),
        MissionItem("m4", "☕", "Кофейный тур пройден", 300, done = true),
    ),
    leaderboard = listOf(
        LeaderboardEntry(1, "Есиль", "💜", 9870),
        LeaderboardEntry(2, "Байконыр", "🌆", 8120),
        LeaderboardEntry(3, "Алматы", "🌲", 7680),
        LeaderboardEntry(4, "Нура", "🌊", 5430, isCurrentUser = true),
        LeaderboardEntry(5, "Сарыарка", "⭐", 4210),
    ),
)
