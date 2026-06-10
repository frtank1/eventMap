package com.eventmap.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.SecondaryButton
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenEvent: (String) -> Unit = {},
    onEditProfile: (name: String, bio: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
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
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ProfileHeader(p)
        Spacer(Modifier.height(16.dp))
        StatsRow(p, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Редактировать", onClick = { onEditProfile(p.name, p.bio) }, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Настройки", onClick = onOpenSettings, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        TabRow(tab, onSelect = { tab = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            when (tab) {
                0 -> {
                    if (history.isEmpty()) Empty("История пуста")
                    else history.forEach { HistoryRow(it, onClick = { onOpenEvent(it.eventId) }) }
                }
                else -> Empty("Вы ещё не создавали события")
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.padding(16.dp)) {
            SecondaryButton(text = "Выйти", onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileHeader(p: UserProfile) {
    Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.linearGradient(EventMapTheme.colors.brandGradient)))
    Column(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text(p.name.take(1), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (p.isVerified) { Spacer(Modifier.size(4.dp)); Text("✓", color = EventMapTheme.colors.info) }
        }
        Text(p.username, style = MaterialTheme.typography.bodyMedium, color = EventMapTheme.colors.textSecondary)
        if (p.bio.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(p.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

@Composable
private fun StatsRow(p: UserProfile, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat(p.eventsCount, "События")
        Stat(p.subscriptions, "Подписки")
        Stat(p.subscribers, "Подписчики")
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textSecondary)
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf("История", "Создано")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                    .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, style = MaterialTheme.typography.labelLarge, color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("🎫") }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text("${item.category} · ${item.dateLabel}", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = EventMapTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
