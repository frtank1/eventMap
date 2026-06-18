package com.eventmap.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.eventmap.profile.models.HistoryItem
import com.eventmap.profile.models.UserProfile
import com.eventmap.profile.ui.viewmodel.ProfileViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  H1 — Мой профиль (Aurora Violet, ночная тема)
// =====================================================================

private val profileTabs = listOf("События", "Галерея", "Рекоменд.", "История", "Видео")

@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
    onEditProfile: (name: String, bio: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenOrganizer: () -> Unit = {},
) {
    val vm = remember { getKoin().get<ProfileViewModel>() }
    val profile by vm.profile.collectAsState()
    val history by vm.history.collectAsState()
    val loading by vm.loading.collectAsState()
    val loggedOut by vm.loggedOut.collectAsState()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    val p = profile
    if (loading || p == null) {
        Box(modifier.fillMaxSize().background(NightBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(NightBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(p = p, onEdit = { onEditProfile(p.name, p.bio) }, onSettings = onOpenSettings)
        Spacer(Modifier.height(16.dp))
        StatsRow(p, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        ProfileTabs(selected = tab, onSelect = { tab = it }, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (tab) {
                3 -> {
                    if (history.isEmpty()) EmptyState("📭", "История посещений пуста")
                    else history.forEach { HistoryRow(it, onClick = { onOpenEvent(it.eventId) }) }
                }
                0 -> EmptyState("🗓️", "Ещё нет созданных событий")
                1 -> EmptyState("🖼️", "Галерея пока пустая")
                2 -> EmptyState("⭐", "Рекомендации появятся после посещений")
                4 -> EmptyState("🎬", "Видео ещё не опубликованы")
            }
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickableNoRipple { onOpenOrganizer() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Кабинет организатора", color = Color(0xFFB794F6), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickableNoRipple { vm.logout() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Выйти из аккаунта", color = Color(0xFFE24B4A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Шапка: аватар, имя, level pill, кнопки
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(p: UserProfile, onEdit: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Аватар с градиентным бордером
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(NightSurface)
                .border(2.dp, Brush.linearGradient(BrandGradient), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Имя + галочка верификации
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            if (p.isVerified) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Verified, contentDescription = "Подтверждён", tint = Info, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            "@${p.username.ifBlank { p.name.lowercase().replace(" ", "") }}",
            fontSize = 13.sp,
            color = NightTextSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (p.bio.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                p.bio,
                fontSize = 13.sp,
                color = NightTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Пилюля уровня
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Violet600.copy(alpha = 0.18f))
                .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏅", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("Ур.7 · Городской исследователь", color = Violet400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        // Кнопки «Редактировать» + ⚙
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple(onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text("Редактировать", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple(onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = NightTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Статистика
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(p: UserProfile, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurface)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(p.subscriptions, "Подписки")
        StatDivider()
        StatItem(p.subscribers, "Подписчики")
        StatDivider()
        StatItem(p.eventsCount, "События")
        StatDivider()
        StatItem(0, "Видео")
    }
}

@Composable
private fun StatItem(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.08f)))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Горизонтальные табы с underline-индикатором
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(profileTabs) { i, label ->
            val active = i == selected
            Column(
                modifier = Modifier
                    .clickableNoRipple { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (active) Color.White else NightTextSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                if (active) {
                    Box(
                        Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(BrandGradient)),
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

// ─────────────────────────────────────────────────────────────────────────────
//  История
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(NightSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = Violet400, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                buildString {
                    if (item.category.isNotBlank()) append("${item.category} · ")
                    append(item.dateLabel)
                },
                fontSize = 11.sp,
                color = NightTextTertiary,
            )
        }
        Text("📍 был", fontSize = 11.sp, color = Soft, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(emoji, fontSize = 36.sp)
        Text(text, color = NightTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
