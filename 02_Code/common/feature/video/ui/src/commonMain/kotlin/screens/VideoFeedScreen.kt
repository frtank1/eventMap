package com.eventmap.video.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.core.platform.share.ShareService
import com.eventmap.event.models.EventDetail
import com.eventmap.event.ui.screens.EventCommentsScreen
import com.eventmap.event.ui.screens.EventReelScreen
import com.eventmap.theme.*
import com.eventmap.video.models.VideoItem
import com.eventmap.video.ui.viewmodel.VideoViewModel
import com.eventmap.views.video.VideoPlaybackState
import com.eventmap.views.video.VideoPlayer
import com.eventmap.views.video.videoTimeLabel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin

class VideoFeedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        VideoFeedContent(onBack = { nav.pop() })
    }
}

/** Same screen in the tab and in navigation: no overlapping notification/back controls. */
@Composable
fun Screen.VideoFeedContent(onNotifications: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    val nav = LocalNavigator.currentOrThrow
    val vm = koinScreenModel<VideoViewModel>()
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.errorText.collectAsState()
    val commentCounts by vm.commentCounts.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val share = remember { getKoin().get<ShareService>() }
    LaunchedEffect(Unit) { vm.load() }
    VideoFeedLayout(
        items = items, loading = loading, error = error, commentCounts = commentCounts,
        active = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
        onRetry = vm::load, onLike = vm::toggleLike, onVisible = vm::loadCommentCount,
        onOpenEvent = { nav.push(EventReelScreen(listOf(EventDetail(id = it.eventId, title = it.eventTitle)))) },
        onComments = { nav.push(EventCommentsScreen(it.eventId)) },
        onShare = { item -> share.shareText("«${item.eventTitle.ifBlank { "Событие" }}» — нашёл в EventMap\n${item.caption}") },
        onNotifications = onNotifications, onBack = onBack,
    )
}

/** Presentation-only entry point, also usable by an isolated debug preview. */
@Composable
fun VideoFeedLayout(
    items: List<VideoItem>, loading: Boolean, error: String?, commentCounts: Map<String, Int> = emptyMap(),
    active: Boolean = true,
    onRetry: () -> Unit, onLike: (String) -> Unit, onVisible: (String) -> Unit,
    onOpenEvent: (VideoItem) -> Unit, onComments: (VideoItem) -> Unit, onShare: (VideoItem) -> Unit,
    onNotifications: (() -> Unit)? = null, onBack: (() -> Unit)? = null,
) {
    val pager = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val current = items.getOrNull(pager.settledPage)
    LaunchedEffect(current?.eventId, active) {
        if (active) current?.eventId?.takeIf { it.isNotBlank() }?.let(onVisible)
    }
    Box(Modifier.fillMaxSize().background(NightBackground)) {
        if (items.isNotEmpty()) {
            VerticalPager(state = pager, key = { items[it].id }, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                VideoPage(
                    item = item, playing = active && pager.settledPage == page && !pager.isScrollInProgress,
                    commentCount = commentCounts[item.eventId],
                    onLike = { onLike(item.id) }, onComments = { onComments(item) },
                    onShare = { onShare(item) }, onOpenEvent = { onOpenEvent(item) },
                    hasNext = page < items.lastIndex,
                    onNext = { scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(items.lastIndex)) } },
                )
            }
        } else Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
            else {
                Icon(EventMapIcons.Video, null, tint = Violet400, modifier = Modifier.size(44.dp))
                Text(if (error != null) "Не удалось загрузить моменты" else "Здесь будет жизнь города",
                    color = NightTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(error ?: "Готовые ролики участников появятся здесь после обработки.",
                    color = NightTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = onRetry) { Text("Обновить") }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(NightBackground.copy(alpha = .92f), Color.Transparent)))
                .padding(start = 20.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) FeedIconButton(EventMapIcons.Back, "Назад", onBack)
            Column(Modifier.weight(1f)) {
                Text("Сейчас", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-.8).sp)
                Text("Город глазами участников", fontSize = 12.sp, color = Color(0xFFCCCEE1))
            }
            if (onNotifications != null) FeedIconButton(EventMapIcons.Bell, "Уведомления", onNotifications)
            else FeedIconButton(EventMapIcons.Refresh, "Обновить ролики", onRetry)
        }
        if (error != null && items.isNotEmpty()) Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 105.dp, start = 16.dp, end = 16.dp),
            color = NightRaised, shape = RoundedCornerShape(14.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), color = NightTextPrimary, fontSize = 12.sp, maxLines = 3)
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
        }
    }
}

@Composable
private fun VideoPage(
    item: VideoItem, playing: Boolean, commentCount: Int?, onLike: () -> Unit,
    onComments: () -> Unit, onShare: () -> Unit, onOpenEvent: () -> Unit, hasNext: Boolean, onNext: () -> Unit,
) {
    var paused by rememberSaveable(item.id) { mutableStateOf(false) }
    var muted by rememberSaveable(item.id) { mutableStateOf(false) }
    var playback by remember(item.id, item.mediaUrl) { mutableStateOf(VideoPlaybackState()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 600.dp
        if (item.mediaUrl.isNotBlank()) VideoPlayer(
            item.mediaUrl, Modifier.fillMaxSize(), playing = playing && !paused, muted = muted,
            onPlaybackState = { playback = it },
        ) else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF333B59), Color(0xFF143A3B), NightBackground)))) {
            Text("Ролик временно недоступен", Modifier.align(Alignment.Center).padding(32.dp), color = NightTextSecondary)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0x55111528), .35f to Color.Transparent, .55f to Color.Transparent, 1f to Color(0xF5080D21),
        )))
        if (item.mediaUrl.isNotBlank() && !playback.failed && !playback.buffering) IconButton(
            onClick = { paused = !paused },
            modifier = Modifier.align(Alignment.Center).offset(y = (-48).dp).size(56.dp)
                .clip(CircleShape).background(Color.White.copy(alpha = .12f)).border(1.dp, Color.White.copy(alpha = .3f), CircleShape),
        ) { Icon(if (paused) EventMapIcons.Play else EventMapIcons.Pause,
            if (paused) "Воспроизвести" else "Пауза", tint = Color.White, modifier = Modifier.size(27.dp)) }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF4A3D70))
                            .border(1.5.dp, Color(0xFF49516B), CircleShape), contentAlignment = Alignment.Center) {
                            Text(item.authorName.take(1).uppercase().ifBlank { "У" }, color = Color(0xFFE3D6FF), fontWeight = FontWeight.Bold)
                        }
                        Text(item.authorName.ifBlank { "Участник" }, color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.caption.isNotBlank()) Text(item.caption, color = Color.White, fontSize = 15.sp,
                        lineHeight = 22.sp, maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReactionButton(EventMapIcons.Heart, item.likes.toString(), if (item.liked) "Убрать лайк" else "Нравится",
                        onLike, tint = if (item.liked) Coral500 else Color.White)
                    if (item.eventId.isNotBlank()) {
                        ReactionButton(EventMapIcons.Comment, commentCount?.toString() ?: "", "Комментарии события", onComments)
                        ReactionButton(EventMapIcons.Share, "", "Поделиться событием", onShare)
                    }
                }
            }
            if (item.eventId.isNotBlank()) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(NightSurface.copy(alpha = .8f))
                    .border(1.dp, Color(0xFF3B4963), RoundedCornerShape(17.dp))
                    .clickable(role = Role.Button, onClick = onOpenEvent).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(NightAccentSurface), contentAlignment = Alignment.Center) {
                    Icon(EventMapIcons.Place, null, tint = Violet400, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("РОЛИК ИЗ СОБЫТИЯ", color = Violet400, fontSize = 10.sp)
                    Text(item.eventTitle.ifBlank { "Открыть событие" }, color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(EventMapIcons.Chevron, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (playback.durationMs > 0) LinearProgressIndicator(
                progress = { playback.progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFC8B3FF), trackColor = Color.White.copy(alpha = .18f),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (playback.durationMs > 0) "${videoTimeLabel(playback.positionMs)} / ${videoTimeLabel(playback.durationMs)}" else "",
                    Modifier.weight(1f), color = Color(0xFFB3BFD1), fontSize = 11.sp)
                if (item.mediaUrl.isNotBlank()) IconButton(onClick = { muted = !muted }) {
                    Icon(if (muted) EventMapIcons.Muted else EventMapIcons.Volume,
                        if (muted) "Включить звук" else "Выключить звук", tint = NightTextSecondary, modifier = Modifier.size(20.dp))
                }
                if (hasNext) TextButton(onClick = onNext) {
                    Text("Другой момент", color = NightTextSecondary, fontSize = 11.sp)
                    Icon(EventMapIcons.Chevron, null, tint = NightTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReactionButton(icon: ImageVector, label: String, description: String, onClick: () -> Unit, tint: Color = Color.White) {
    Column(Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
        .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(27.dp))
        if (label.isNotBlank()) Text(label, color = tint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun FeedIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF1E2B42)).border(1.dp, Color(0xFF45506B), RoundedCornerShape(14.dp))) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
