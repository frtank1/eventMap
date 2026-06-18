package com.eventmap.feed.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.feed.models.NotificationItem
import com.eventmap.feed.ui.viewmodel.NotificationsViewModel
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  F5 · Уведомления (Aurora Violet, ночная тема)
// =====================================================================

@Composable
fun NotificationsContent(modifier: Modifier = Modifier) {
    val vm = remember { getKoin().get<NotificationsViewModel>() }
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .background(NightBackground),
    ) {
        Text(
            "Уведомления",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(top = 56.dp, start = 20.dp, bottom = 16.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
            }
            items.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🔔", fontSize = 52.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Уведомлений пока нет", color = NightTextSecondary, fontSize = 14.sp)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { n ->
                    NotificationRow(n)
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (n.read) NightSurface
                else Violet600.copy(alpha = 0.12f),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Иконка
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (n.read) Color.White.copy(alpha = 0.06f)
                    else Violet600.copy(alpha = 0.20f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(iconFor(n.type), fontSize = 18.sp)
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                n.text,
                fontSize = 14.sp,
                color = if (n.read) Color.White.copy(alpha = 0.75f) else Color.White,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.Medium,
                lineHeight = 18.sp,
            )
            if (n.timeLabel.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(n.timeLabel, fontSize = 11.sp, color = NightTextTertiary)
            }
        }

        // Точка непрочитанного
        if (!n.read) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Violet400))
        }
    }
}

private fun iconFor(type: String): String = when (type) {
    "live"      -> "🔴"
    "subscribe" -> "🔔"
    "event"     -> "📅"
    else        -> "ℹ️"
}
