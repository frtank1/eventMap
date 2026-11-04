package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import com.eventmap.views.media.RemoteMediaImage
import com.eventmap.views.video.VideoPlayer
import com.eventmap.base_screen.RequestErrorBanner
import kotlinx.coroutines.delay
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
import com.eventmap.event.models.MediaItem
import com.eventmap.event.ui.viewmodel.EventDetailViewModel
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  D2 — Галерея события (фото/видео 3-колонки)
//  Figma «🎬 D · Событие»  node 51:2
// =====================================================================

data class EventGalleryScreen(val eventId: String) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<EventDetailViewModel>()
        val media by vm.media.collectAsState()
        val event by vm.event.collectAsState()
        var selected by remember { mutableStateOf<MediaItem?>(null) }

        LaunchedEffect(eventId) {
            vm.load(eventId, force = true)
            while (true) {
                delay(10_000)
                if (vm.media.value.any { it.status in setOf("uploading", "processing") }) vm.refreshMedia()
            }
        }
        selected?.let { item ->
            Dialog(onDismissRequest = { selected = null }) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.6f).background(Color.Black)) {
                    if (item.type == "video") VideoPlayer(item.url, Modifier.fillMaxSize())
                    else RemoteMediaImage(item.url, Modifier.fillMaxSize())
                    Text("Закрыть", color = Color.White, modifier = Modifier.align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f)).clickableNoRipple { selected = null }.padding(16.dp))
                }
            }
        }

        Column(
            Modifier.fillMaxSize().background(NightBackground),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${event?.title ?: "Событие"} · галерея",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${media.size} медиа",
                        color = NightTextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            RequestErrorBanner(vm, onRetry = vm::reload)
            if (media.isEmpty()) Text("Пока нет фото и видео", color = NightTextSecondary, modifier = Modifier.padding(16.dp))

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(media, key = { it.id }) { item ->
                    GalleryCell(item) { if (item.status == "ready" && item.url.isNotBlank()) selected = item }
                }
            }

            // CTA add media
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple {
                        nav.push(AddMediaScreen(eventId))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "＋ Добавить своё",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun GalleryCell(item: MediaItem, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        RemoteMediaImage(if (item.type == "video") item.thumbnailUrl else item.url, Modifier.fillMaxSize())
        if (item.type == "video" && item.status == "ready") {
            Text("▶", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        if (item.status != "ready") {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(if (item.status in setOf("uploading", "processing")) "Обработка…" else "Недоступно", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}
