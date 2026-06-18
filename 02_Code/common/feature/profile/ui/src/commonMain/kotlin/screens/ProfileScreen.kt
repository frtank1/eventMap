package com.eventmap.profile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.eventmap.theme.Info
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  H1 · Мой профиль
//  H2 · Редактировать профиль
//  H3 · Профиль другого пользователя
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class ProfileStats(val label: String, val value: String)
private data class BadgeItem(val emoji: String, val label: String, val unlocked: Boolean)
private data class VideoItem(val id: String, val emoji: String, val views: String, val isLive: Boolean = false)

private val myStats = listOf(
    ProfileStats("События", "47"),
    ProfileStats("Подписчики", "1 240"),
    ProfileStats("Подписки", "318"),
    ProfileStats("XP", "4 820"),
)

private val myBadges = listOf(
    BadgeItem("🥇", "Первопроходец", true),
    BadgeItem("🎵", "Меломан", true),
    BadgeItem("🌟", "Звезда", true),
    BadgeItem("🏆", "Чемпион", false),
    BadgeItem("🎯", "Снайпер", false),
)

private val myVideos = List(9) { i ->
    VideoItem("v$i", listOf("🎵", "🎨", "🛍", "🏋️", "🎤", "🎬", "☕", "🎮", "🌿")[i], "${(i + 1) * 140}",
        isLive = i == 0)
}

// ─────────────────────────────────────────────────────────────────────────────
//  H1: ProfileScreen (собственный профиль)
// ─────────────────────────────────────────────────────────────────────────────

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Видео", "Бейджи", "Карта")

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Профиль", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBtn("🔔") {}
                    IconBtn("⚙️") {}
                }
            }

            Spacer(Modifier.height(20.dp))

            // Аватар + имя
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape)
                            .background(Brush.linearGradient(BrandGradient))
                            .border(3.dp, NightBackground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("АТ", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
                    // Уровень
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(Color(0xFFFFD700))
                            .border(2.dp, NightBackground, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("12", fontSize = 11.sp, color = Color(0xFF1A1200), fontWeight = FontWeight.ExtraBold) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Azamat Temirzhan", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("@azamat_t · Алматы", fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(8.dp))
                Text("🗺 Картограф событий · Разработчик EventMap", fontSize = 12.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 17.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Статистика
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                myStats.forEach { stat ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stat.value, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text(stat.label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Кнопки
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.push(EditProfileV2Screen()) },
                    contentAlignment = Alignment.Center,
                ) { Text("Редактировать", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("Поделиться", color = Color.White, fontSize = 14.sp) }
            }

            Spacer(Modifier.height(20.dp))

            // Tabs
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(4.dp),
            ) {
                tabs.forEachIndexed { i, label ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickableNoRipple { selectedTab = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) Color.White else NightTextSecondary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                0 -> VideoGrid(myVideos)
                1 -> BadgesSection(myBadges)
                2 -> MapActivitySection()
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun VideoGrid(videos: List<VideoItem>) {
    // 3 col grid
    val rows = videos.chunked(3)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { video ->
                    Box(
                        Modifier.weight(1f).aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1A0D2A), NightSurface)
                                )
                            ).clickableNoRipple {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(video.emoji, fontSize = 28.sp)
                        if (video.isLive) {
                            Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFFF4757).copy(alpha = 0.9f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                Text("LIVE", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Box(Modifier.align(Alignment.BottomStart).padding(5.dp)) {
                            Text("▶ ${video.views}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                // fill row
                repeat(3 - row.size) { Box(Modifier.weight(1f).aspectRatio(9f / 16f)) }
            }
        }
    }
}

@Composable
private fun BadgesSection(badges: List<BadgeItem>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(badges) { badge ->
            Column(
                Modifier.width(80.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (badge.unlocked) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(badge.emoji, fontSize = 28.sp)
                Text(badge.label, fontSize = 10.sp, color = if (badge.unlocked) Color.White else NightTextSecondary,
                    textAlign = TextAlign.Center, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun MapActivitySection() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(200.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1F2D)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🗺️", fontSize = 40.sp)
            Text("47 мест на карте", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Все события, где ты побывал", fontSize = 12.sp, color = NightTextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  H2: EditProfileScreen
// ─────────────────────────────────────────────────────────────────────────────

/** H2 v2 — переработанный экран редактирования профиля */
class EditProfileV2Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var name by remember { mutableStateOf("Azamat Temirzhan") }
        var username by remember { mutableStateOf("azamat_t") }
        var bio by remember { mutableStateOf("🗺 Картограф событий · Разработчик EventMap") }
        var city by remember { mutableStateOf("Алматы") }
        var website by remember { mutableStateOf("") }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("Редактировать профиль", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { nav.pop() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Сохранить", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Аватар
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                            .border(3.dp, NightBackground, CircleShape).clickableNoRipple {},
                        contentAlignment = Alignment.Center,
                    ) { Text("АТ", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(NightSurface)
                            .border(2.dp, NightBackground, CircleShape).clickableNoRipple {},
                        contentAlignment = Alignment.Center,
                    ) { Text("📷", fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Имя пользователя") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("@", color = NightTextSecondary) })
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("О себе") },
                    minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Город") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Сайт / соцсети") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            // Интересы
            SLabel2("ИНТЕРЕСЫ")
            val interests = listOf("🎵 Музыка", "🎨 Арт", "🏋️ Спорт", "🎤 Стендап")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(interests) { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(tag, fontSize = 13.sp, color = Color.White) }
                }
                item {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(NightSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                            .clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text("+ Добавить", fontSize = 13.sp, color = NightTextSecondary) }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  H3: OtherProfileScreen (чужой профиль)
// ─────────────────────────────────────────────────────────────────────────────

data class OtherProfileScreen(val userId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var isFollowing by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(0) }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            // Топ-бар
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("@daniyar_kz", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                IconBtn("⋯") {}
            }

            Spacer(Modifier.height(16.dp))

            // Аватар + имя
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF0D2A1A), Color(0xFF0A1420))))
                        .border(3.dp, NightBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("ДА", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(10.dp))
                Text("Данияр Асанов", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("@daniyar_kz · Астана", fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(6.dp))
                Text("🏙 Городской исследователь · 120 городских активностей", fontSize = 12.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Статистика
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("120" to "События", "3 400" to "Подписчики", "210" to "Подписки").forEach { (val_, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(val_, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text(label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Кнопки
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isFollowing) Brush.linearGradient(listOf(NightSurface, NightSurface)) else Brush.linearGradient(BrandGradient))
                        .border(if (isFollowing) 1.dp else 0.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickableNoRipple { isFollowing = !isFollowing },
                    contentAlignment = Alignment.Center,
                ) { Text(if (isFollowing) "Отписаться" else "Подписаться", color = if (isFollowing) NightTextSecondary else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("Написать", color = Color.White, fontSize = 14.sp) }
            }

            Spacer(Modifier.height(20.dp))

            // Tabs
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(4.dp),
            ) {
                listOf("Видео", "Бейджи").forEachIndexed { i, label ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickableNoRipple { selectedTab = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) Color.White else NightTextSecondary, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> VideoGrid(myVideos.take(6))
                1 -> BadgesSection(myBadges)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun IconBtn(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 16.sp) }
}

@Composable
private fun SLabel2(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
}
