package com.eventmap.video.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.LiveGradient
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import com.eventmap.video.models.VideoItem
import com.eventmap.views.video.VideoPlayer
import com.eventmap.video.ui.viewmodel.VideoViewModel

// =====================================================================
//  E1 «Прямо сейчас» — вертикальная лента live-рилсов (TikTok-стиль)
//  E2 Плеер из события — контекст-пилюля «Из события · …»
// =====================================================================

class VideoFeedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<VideoViewModel>()
        val items by vm.items.collectAsState()
        val loading by vm.loading.collectAsState()
        val error by vm.errorText.collectAsState()
        // Re-entering the screen fetches fresh signed playback URLs.
        LaunchedEffect(Unit) { vm.load() }

        Box(Modifier.fillMaxSize().background(Color.Black)) {

            when {
                error != null && items.isEmpty() -> com.eventmap.base_screen.RequestErrorBanner(
                    vm, onRetry = vm::load, modifier = Modifier.align(Alignment.Center),
                )
                loading -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = NightTextSecondary, modifier = Modifier.size(48.dp))
                    Text("Пока нет готовых роликов", color = NightTextSecondary, fontSize = 14.sp)
                    Text("После обработки видео появится здесь", color = NightTextSecondary, fontSize = 12.sp)
                    Text("Обновить", color = Color.White, modifier = Modifier.clickableNoRipple { vm.load() }.padding(12.dp))
                }
                else -> {
                    val pager = rememberPagerState(pageCount = { items.size })
                    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        VideoPage(
                            item = items[page],
                            playing = pager.currentPage == page, // играет только видимая страница
                            onLike = { vm.toggleLike(items[page].id) },
                            onMap = { nav.pop() },
                        )
                    }
                }
            }

            if (items.isNotEmpty()) com.eventmap.base_screen.RequestErrorBanner(
                vm, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
            )
            // ── Топ-бар ─────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(top = 52.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    LivePulseDot()
                    Spacer(Modifier.width(6.dp))
                    Text("Прямо сейчас", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { vm.load() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Refresh, contentDescription = "Обновить ролики", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Один слайд VerticalPager
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoPage(item: VideoItem, playing: Boolean, onLike: () -> Unit, onMap: () -> Unit) {
    Box(Modifier.fillMaxSize()) {

        // Видео: реальный плеер (ExoPlayer/AVPlayer); нет mediaUrl — плейсхолдер
        if (item.mediaUrl.isNotBlank()) {
            VideoPlayer(
                url = item.mediaUrl,
                playing = playing,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF0D0818), Color(0xFF1A0D2E), Color(0xFF0A0D1A))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ролик временно недоступен. Обновите ленту", color = NightTextSecondary, modifier = Modifier.padding(24.dp))
            }
        }

        // Нижний скрим
        Box(
            Modifier.fillMaxWidth().height(340.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))),
        )

        // ── Правый столбец реакций ───────────────────────────────────
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReactionBtn(if (item.liked) "❤️" else "🤍", item.likes.takeIf { it > 0 }?.toString() ?: "", onLike)
            ReactionBtn("💬", "0") {}
            ReactionBtn("↗️", "") {}
            // 📍 Карта
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onMap)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = "Карта", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("карта", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }

        // ── Нижняя секция: автор + событие + подпись ─────────────────
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 72.dp, bottom = 100.dp)) {
            // LIVE-пилюля
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(LiveGradient))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePulseDot()
                Spacer(Modifier.width(5.dp))
                Text(
                    "LIVE · ${formatViewers(item.viewers)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Автор + «Следить»
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.authorName.take(1).uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(item.authorName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f))
                        .clickableNoRipple { }.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Следить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Контекст-пилюля события (E2)
            if (item.eventTitle.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Violet600.copy(alpha = 0.30f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📍", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Из события · ${item.eventTitle}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("›", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
            }

            if (item.caption.isNotBlank()) {
                Text(item.caption, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ReactionBtn(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 22.sp) }
        if (label.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LivePulseDot() {
    val t = rememberInfiniteTransition()
    val pulse by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Box(Modifier.size(7.dp).scale(pulse).alpha(pulse).clip(CircleShape).background(Color.White))
}

private fun formatViewers(n: Int): String = when {
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}к"
    n > 0     -> "$n"
    else      -> "0"
}
