package com.eventmap.feed.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.feed.models.SubscriptionItem
import com.eventmap.feed.models.SubscriptionRequest
import com.eventmap.feed.ui.viewmodel.SubTab
import com.eventmap.feed.ui.viewmodel.SubscriptionsViewModel
import com.eventmap.theme.EventMapTheme

class SubscriptionsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SubscriptionsViewModel>()
        val tab by vm.tab.collectAsState()
        val items by vm.items.collectAsState()
        val requests by vm.requests.collectAsState()

        Scaffold { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.clickable { nav.pop() })
                    Spacer(Modifier.size(12.dp))
                    Text("Подписки", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }

                TabRow(tab, onSelect = { vm.setTab(it) }, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))

                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (requests.isNotEmpty()) {
                        item {
                            Text("Запросы на подписку", style = MaterialTheme.typography.labelLarge, color = EventMapTheme.colors.textSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(requests) { r -> RequestRow(r, onApprove = { vm.approve(r.id) }, onReject = { vm.reject(r.id) }) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    items(items) { s -> SubRow(s) }
                }
            }
        }
    }
}

@Composable
private fun TabRow(selected: SubTab, onSelect: (SubTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubTab.entries.forEach { t ->
            val sel = t == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                    .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(t) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SubRow(s: SubscriptionItem) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(s.emoji.ifBlank { "•" }, fontSize = 18.sp) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (s.subtitle.isNotBlank()) Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary, maxLines = 1)
        }
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Отписаться", style = MaterialTheme.typography.labelMedium, color = EventMapTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun RequestRow(r: SubscriptionRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EventMapTheme.colors.accent.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(r.name.take(1), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (r.subtitle.isNotBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
        }
        Text("✓", color = EventMapTheme.colors.soft, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onApprove).padding(horizontal = 8.dp))
        Text("✕", color = EventMapTheme.colors.danger, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onReject).padding(horizontal = 8.dp))
    }
}
