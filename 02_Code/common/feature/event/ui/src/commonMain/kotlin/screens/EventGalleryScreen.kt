package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

        LaunchedEffect(eventId) { vm.load(eventId) }

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
                        "Стендап-вечер · галерея",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${media.size} медиа · 12 live прямо сейчас",
                        color = NightTextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (media.isEmpty()) {
                    // Stub items to match Figma
                    items(9) { i ->
                        GalleryCell(isLive = i < 3, isVideo = true)
                    }
                } else {
                    items(media) { item ->
                        GalleryCell(isLive = false, isVideo = item.type == "video")
                    }
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
private fun GalleryCell(isLive: Boolean, isVideo: Boolean) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        // Video play icon
        if (isVideo) {
            Text("▶", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        // LIVE badge
        if (isLive) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Live)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
