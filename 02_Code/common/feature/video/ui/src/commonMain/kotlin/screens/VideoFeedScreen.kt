package com.eventmap.video.ui.screens

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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.video.models.VideoItem
import com.eventmap.video.ui.viewmodel.VideoViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon

class VideoFeedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<VideoViewModel>()
        val items by vm.items.collectAsState()
        val loading by vm.loading.collectAsState()

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else if (items.isEmpty()) {
                Text("Сейчас никто не в эфире", color = Color.White, modifier = Modifier.align(Alignment.Center))
            } else {
                val pager = rememberPagerState(pageCount = { items.size })
                VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    VideoPage(items[page], onLike = { vm.toggleLike(items[page].id) })
                }
            }

            // Топ-бар
            Row(
                Modifier.fillMaxWidth().padding(top = 44.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nav.pop() })
                Text("Прямо сейчас", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("⦿", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { nav.push(RecordVideoScreen()) })
            }
        }
    }
}

@Composable
private fun VideoPage(item: VideoItem, onLike: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // плейсхолдер видео (реальный плеер — после подключения стрим-инфры)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF1A1030), Color(0xFF301030), Color(0xFF101020))),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(72.dp)) }

        // правый столбец реакций
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ReactionButton(if (item.liked) "❤️" else "🤍", "${item.likes}", onLike)
            ReactionButton("💬", "0") {}
            ReactionButton("↗", "") {}
        }

        // низ: автор/событие/подпись
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 80.dp, bottom = 90.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(item.authorName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(8.dp))
                Text(item.authorName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFF4757)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("LIVE · ${item.viewers}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(item.eventTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (item.caption.isNotBlank()) Text(item.caption, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun ReactionButton(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(icon, fontSize = 28.sp)
        if (label.isNotBlank()) Text(label, color = Color.White, fontSize = 11.sp)
    }
}
