package com.eventmap.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.feed.models.FeedItem
import com.eventmap.feed.ui.viewmodel.FeedViewModel
import com.eventmap.theme.EventMapTheme
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun FeedContent(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
) {
    val vm = remember { getKoin().get<FeedViewModel>() }
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Лента", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Подписки", style = MaterialTheme.typography.labelLarge, color = EventMapTheme.colors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onOpenSubscriptions))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item -> FeedCard(item, onClick = { onOpenEvent(item.eventId) }) }
        }
    }
}

@Composable
private fun FeedCard(item: FeedItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(EventMapTheme.colors.brandGradient)), contentAlignment = Alignment.Center) {
            Text(item.category.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text("${item.organizerName} · ${item.timeLabel}", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textSecondary, maxLines = 1)
        }
    }
}
