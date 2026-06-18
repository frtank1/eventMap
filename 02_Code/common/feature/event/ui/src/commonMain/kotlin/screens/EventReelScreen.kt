package com.eventmap.event.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventStatus
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.delay

// =====================================================================
//  D1a / D1b / D1c — Event Reel  (Figma «🎬 D · Событие»)
//  Swipe ↕ between events · bottom sheet collapsed ↔ expanded
//  D9 — Comments sheet · D10 — Share sheet · D11 — Bell toast
// =====================================================================

/** D1c CTA states */
enum class ReelCta { Going, Buy, ImHere, TooFar }

fun ctaFor(event: EventDetail, distanceM: Int?): ReelCta = when {
    event.statusEnum() == EventStatus.Live ->
        if (distanceM != null && distanceM <= 300) ReelCta.ImHere else ReelCta.TooFar
    !event.price.isFree -> ReelCta.Buy
    else -> ReelCta.Going
}

private enum class OverlaySheet { None, Comments, Share }

data class EventReelScreen(
    val events: List<EventDetail>,
    val startIndex: Int = 0,
) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val pager = rememberPagerState(initialPage = startIndex) { events.size }

        Box(Modifier.fillMaxSize().background(NightBackground)) {
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ReelPage(
                    event = events[page],
                    distanceM = null,
                    onBack = { nav.pop() },
                    onOpenGallery = { nav.push(EventGalleryScreen(events[page].id)) },
                    onOpenParticipants = { nav.push(EventParticipantsScreen(events[page].id)) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single Reel Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReelPage(
    event: EventDetail,
    distanceM: Int?,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenParticipants: () -> Unit,
) {
    var sheetExpanded by remember { mutableStateOf(false) }
    var overlaySheet by remember { mutableStateOf(OverlaySheet.None) }
    var bellEnabled by remember { mutableStateOf(false) }
    var showBellToast by remember { mutableStateOf(false) }
    val cta = ctaFor(event, distanceM)

    // D11 — Bell toast auto-dismiss
    LaunchedEffect(showBellToast) {
        if (showBellToast) {
            delay(2500)
            showBellToast = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ── Video gradient placeholder ────────────────────────────────────
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A1F4D), Color(0xFF10131F)),
                    start = Offset(0f, 0f),
                    end = Offset(300f, 800f),
                ),
            ),
        )

        // Top gradient shade
        Box(
            Modifier.fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent))),
        )

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconBtn("‹", onBack)
            Spacer(Modifier.width(8.dp))
            if (event.statusEnum() == EventStatus.Live) {
                ReelPill("● LIVE · ${event.liveViewers}", Live)
                Spacer(Modifier.width(6.dp))
            }
            if (event.category.isNotBlank()) {
                ReelPill(event.category, Color.Black.copy(alpha = 0.45f))
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            RoundIconBtn("⋯") { }
        }

        // Feed context pill
        FeedContextPill(
            text = "↕ Похожие → сегодня → завтра",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
        )

        // Play button placeholder
        Text(
            "▶",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 54.sp,
            modifier = Modifier.align(Alignment.Center),
        )

        // ── Action column: ❤️ 💬 ↗ 🔔 ─────────────────────────────────────
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActionBtn(EventMapIcons.Heart, formatCount(event.ratingCount)) { }
            ActionBtn(EventMapIcons.Comment, "340") { overlaySheet = OverlaySheet.Comments }
            ActionBtn(EventMapIcons.Share, null) { overlaySheet = OverlaySheet.Share }
            ActionBtn(
                icon = EventMapIcons.Bell,
                label = null,
                tint = if (bellEnabled) Soft else Color.White,
                onClick = {
                    bellEnabled = !bellEnabled
                    if (bellEnabled) showBellToast = true
                },
            )
        }

        // ── Organizer + title (visible when sheet collapsed) ───────────────
        AnimatedVisibility(
            visible = !sheetExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 196.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(BrandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text("🎤", fontSize = 14.sp) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        event.organizerName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = Soft, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    event.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
                Spacer(Modifier.height(4.dp))
                val infoLine = if (event.statusEnum() == EventStatus.Live)
                    "18 мин в эфире · ${event.liveViewers} человек сейчас"
                else "${event.goingCount} человек идут · ${event.location.name}"
                Text(infoLine, color = Color(0xFFDFE3F0), fontSize = 12.5.sp)
            }
        }

        // ── Bottom sheet ───────────────────────────────────────────────────
        ReelSheet(
            event = event,
            expanded = sheetExpanded,
            onExpandChange = { sheetExpanded = it },
            cta = cta,
            distanceM = distanceM,
            onCta = { /* TODO */ },
            onOpenGallery = onOpenGallery,
            onOpenParticipants = onOpenParticipants,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // ── Overlay sheets ─────────────────────────────────────────────────
        if (overlaySheet != OverlaySheet.None) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickableNoRipple { overlaySheet = OverlaySheet.None },
            )
        }
        if (overlaySheet == OverlaySheet.Comments) {
            CommentsSheet(
                onDismiss = { overlaySheet = OverlaySheet.None },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (overlaySheet == OverlaySheet.Share) {
            ShareSheet(
                onDismiss = { overlaySheet = OverlaySheet.None },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ── D11 Bell toast ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showBellToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(NightSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "✓ Напомним за час до начала «${event.title.take(28)}»",
                    color = Soft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Sheet (D1a collapsed / D1b expanded)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReelSheet(
    event: EventDetail,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    cta: ReelCta,
    distanceM: Int?,
    onCta: (ReelCta) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenParticipants: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height by animateDpAsState(if (expanded) 640.dp else 176.dp, tween(350))
    var dragAcc by remember { mutableStateOf(0f) }
    val drag = rememberDraggableState { delta -> dragAcc += delta }

    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xF70D1623))
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    if (dragAcc < -60f) onExpandChange(true)
                    if (dragAcc > 60f) onExpandChange(false)
                    dragAcc = 0f
                },
            )
            .animateContentSize(),
    ) {
        // Grab handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.28f))
                .clickableNoRipple { onExpandChange(!expanded) },
        )

        if (!expanded) {
            // D1a — collapsed
            Text(
                "▲ потяни вверх — описание",
                color = NightTextSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            )
            Row(
                Modifier.padding(start = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SheetChip("📍 ${event.location.name.take(16)}")
                SheetChip("🕗 ${event.startAt.ifBlank { "скоро" }}")
                SheetChip("🎟️ ${if (event.price.isFree) "Free" else event.price.label()}")
            }
            Spacer(Modifier.height(12.dp))
            CtaBlock(cta, event, distanceM, onCta, Modifier.padding(horizontal = 16.dp))
        } else {
            // D1b — expanded
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                Text(event.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${event.liveViewers} человек сейчас · 18 мин в эфире",
                    color = NightTextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                SheetInfoRow("📍", event.location.name, "ул. Кунаева 12 · ${distanceM ?: "??"} м")
                SheetInfoRow("🕗", "Сегодня ${event.startAt.ifBlank { "20:00–23:00" }}", "идёт сейчас")
                SheetInfoRow(
                    "🎟️",
                    if (event.price.isFree) "Вход свободный" else event.price.label(),
                    if (event.price.isFree) "по регистрации" else "покупка = автоматически «иду»",
                )
                SheetInfoRow("👤", event.organizerName, "организатор · ✓ верифицирован")
                Spacer(Modifier.height(10.dp))
                Text(
                    event.description.ifBlank { "Описание отсутствует." },
                    color = Color(0xFFCDD3E6),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChipBtn("Галерея · 86", onClick = onOpenGallery)
                    TabChipBtn("Участники · ${event.goingCount}", onClick = onOpenParticipants)
                }
                Spacer(Modifier.height(20.dp))
            }
            CtaBlock(cta, event, distanceM, onCta, Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D1c — CTA Block
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CtaBlock(
    cta: ReelCta,
    event: EventDetail,
    distanceM: Int?,
    onCta: (ReelCta) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = cta != ReelCta.TooFar
    val gradient = when (cta) {
        ReelCta.ImHere -> Brush.linearGradient(LiveGradient)
        ReelCta.TooFar -> Brush.linearGradient(listOf(NightSurface, NightSurface))
        else -> Brush.linearGradient(BrandGradient)
    }
    val label = when (cta) {
        ReelCta.Going -> "🙋 Приду"
        ReelCta.Buy -> "🎟️ Купить · ${event.price.label()}"
        ReelCta.ImHere, ReelCta.TooFar -> "📍 Я тоже здесь"
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (cta == ReelCta.ImHere)
                        Modifier.shadow(8.dp, RoundedCornerShape(14.dp),
                            ambientColor = Live.copy(0.4f), spotColor = Live.copy(0.4f))
                    else Modifier,
                )
                .background(gradient)
                .clickableNoRipple { if (isEnabled) onCta(cta) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (isEnabled) Color.White else NightTextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        val hint = when (cta) {
            ReelCta.ImHere -> "✓ ты на месте — локация совпадает"
            ReelCta.TooFar -> distanceM?.let { "ты в $it м — кнопка активируется рядом с событием" }
                ?: "включи геолокацию, чтобы отметиться"
            ReelCta.Buy -> "билет = автоматически в списке «идут» · можно купить друзьям"
            ReelCta.Going -> "попадёшь в список «идут» · напомним к старту"
        }
        Text(
            hint,
            color = if (cta == ReelCta.ImHere) Soft else NightTextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D9 — Comments Sheet
// ─────────────────────────────────────────────────────────────────────────────

private data class StubComment(
    val emoji: String, val user: String, val time: String,
    val text: String, val likes: Int, val isReply: Boolean = false,
)

private val stubComments = listOf(
    StubComment("😎", "damir_a", "2 мин", "Лучший опен-майк в городе, я уже тут 🔥", 24),
    StubComment("🥰", "aliya.k", "5 мин", "Кто едет с Достыка? Возьму +1 в такси", 8),
    StubComment("🎤", "sxodka_comedy", "8 мин", "Сегодня специальный гость, не пропустите 👀", 56),
    StubComment("🤓", "timur_zh", "3 мин", "@aliya.k я! пиши в лс", 2, isReply = true),
    StubComment("😄", "sabina_m", "12 мин", "А вход точно свободный?", 1),
)

@Composable
private fun CommentsSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.67f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF0D1623))
            .clickableNoRipple { /* consume */ },
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                .size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        )
        Text(
            "Комментарии · 340",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(stubComments) { c ->
                Row(
                    Modifier.padding(start = if (c.isReply) 40.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val sz = if (c.isReply) 26.dp else 32.dp
                    Box(
                        Modifier.size(sz).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.emoji, fontSize = if (c.isReply) 12.sp else 14.sp) }
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(c.user, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(c.time, color = NightTextSecondary, fontSize = 11.sp)
                        }
                        Text(c.text, color = Color(0xFFCDD3E6), fontSize = 13.sp, lineHeight = 18.sp)
                        Text("Ответить", color = NightTextSecondary, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♡", color = Color.White, fontSize = 13.sp)
                        Text("${c.likes}", color = NightTextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        // Input bar
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0D1623))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) { Text("🙂", fontSize = 18.sp) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp))
                    .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("Добавь комментарий…", color = NightTextSecondary, fontSize = 13.sp) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("➤", color = Color.White, fontSize = 15.sp) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D10 — Share Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF0D1623))
            .padding(bottom = 32.dp)
            .clickableNoRipple { /* consume */ },
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                .size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        )
        Text(
            "Поделиться событием",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "ДРУЗЬЯМ В EVENTMAP",
            color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for ((emoji, name) in listOf("😎" to "Дамир", "🥰" to "Алия", "🤓" to "Тимур", "😄" to "Сабина", "😀" to "Ержан")) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 22.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = NightTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "В ДРУГИЕ ПРИЛОЖЕНИЯ",
            color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for ((emoji, name) in listOf("✈️" to "Telegram", "💬" to "WhatsApp", "📸" to "Instagram", "🔗" to "Ссылка", "⋯" to "Ещё")) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 22.sp) }
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = NightTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))
                .clickableNoRipple(onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("Отмена", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Small components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RoundIconBtn(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(19.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ReelPill(text: String, bg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) { Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun FeedContextPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) { Text(text, color = Color(0xFFDFE3F0), fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ActionBtn(
    icon: ImageVector,
    label: String?,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SheetChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) { Text(text, color = Color(0xFFDFE3F0), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SheetInfoRow(emoji: String, value: String, caption: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 16.sp) }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(value, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = NightTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TabChipBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

private fun formatCount(n: Int): String = when {
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}k"
    else -> "$n"
}
