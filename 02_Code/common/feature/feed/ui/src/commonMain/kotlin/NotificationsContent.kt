package com.eventmap.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.feed.models.NotificationItem
import com.eventmap.feed.ui.viewmodel.NotificationsViewModel
import com.eventmap.theme.EventMapTheme
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun NotificationsContent(modifier: Modifier = Modifier) {
    val vm = remember { getKoin().get<NotificationsViewModel>() }
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Text("Уведомления", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { n -> NotificationRow(n) }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (n.read) MaterialTheme.colorScheme.surface else EventMapTheme.colors.accent.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(iconFor(n.type), fontSize = 16.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(n.timeLabel, style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
        }
    }
}

private fun iconFor(type: String): String = when (type) {
    "live" -> "🔴"
    "subscribe" -> "🔔"
    "event" -> "📅"
    else -> "ℹ️"
}
