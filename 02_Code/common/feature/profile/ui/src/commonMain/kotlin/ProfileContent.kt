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

private val profileTabs = listOf("События", "История")

@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
    onEditProfile: (name: String, bio: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenOrganizer: () -> Unit = {},
    onOpenMyEvents: () -> Unit = {},
) {
    val vm = remember { getKoin().get<ProfileViewModel>() }
    val profile by vm.profile.collectAsState()
    val history by vm.history.collectAsState()
    val loading by vm.loading.collectAsState()
    val loggedOut by vm.loggedOut.collectAsState()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    val p = profile
    if (loading) {
        Box(modifier.fillMaxSize().background(NightBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
        }
        return
    }
    if (p == null) {
        Column(modifier.fillMaxSize().background(NightBackground).padding(top = 20.dp)) {
            com.eventmap.base_screen.RequestErrorBanner(vm, onRetry = vm::load)
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(NightBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        com.eventmap.base_screen.RequestErrorBanner(vm, onRetry = vm::load)
        ProfileHeader(p = p, onEdit = { onEditProfile(p.name, p.bio) }, onSettings = onOpenSettings)
        Spacer(Modifier.height(16.dp))
        StatsRow(p, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        ProfileTabs(selected = tab, onSelect = { tab = it }, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (tab) {
                1 -> {
                    if (history.isEmpty()) EmptyState("📭", "История посещений пуста")
                    else history.forEach { HistoryRow(it, onClick = { onOpenEvent(it.eventId) }) }
                }
                0 -> {
                    Text("Созданные события, участники и управление публикациями — в разделе «Мои события».",
                        color = NightTextSecondary, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 20.dp))
                }
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
                .clickableNoRipple { onOpenMyEvents() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Мои события · правка и удаление", color = Color(0xFFB794F6), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Профиль", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)
                .clip(RoundedCornerShape(14.dp)).background(NightSurface)
                .border(1.dp, com.eventmap.theme.NightOutline, RoundedCornerShape(14.dp))) {
                Icon(com.eventmap.theme.EventMapIcons.Settings, "Настройки", tint = NightTextSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f, fill = false), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, maxLines = 2)
                    if (p.isVerified) Icon(Icons.Rounded.Verified, "Подтверждён", tint = Info, modifier = Modifier.padding(start = 5.dp).size(17.dp))
                }
                Text("@${p.username.ifBlank { p.name.lowercase().replace(" ", "") }}", color = NightTextSecondary, fontSize = 12.sp)
            }
        }
        if (p.bio.isNotBlank()) Text(p.bio, color = NightTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
        androidx.compose.material3.OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp)) {
            Icon(com.eventmap.theme.EventMapIcons.Edit, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Редактировать профиль", color = Color.White)
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
        Text("Посетил", fontSize = 11.sp, color = Soft, fontWeight = FontWeight.Medium)
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
        Icon(com.eventmap.theme.EventMapIcons.Calendar, null, tint = Violet400, modifier = Modifier.size(36.dp))
        Text(text, color = NightTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
