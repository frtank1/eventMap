package com.eventmap.map.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.Live
import com.eventmap.theme.LiveGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  C · Карта
//  C1 MapScreen — основной экран с картой, категориями и боттом-шитом
//  C2 EventClusterSheet — попап кластера (несколько событий рядом)
//  C2.0 LiveMapScreen — живая карта (реал-тайм режим)
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class MapEvent(
    val id: String,
    val emoji: String,
    val title: String,
    val category: String,
    val time: String,
    val attending: Int,
    val isLive: Boolean = false,
    val isFree: Boolean = false,
    val price: String = "",
)

private val sampleEvents = listOf(
    MapEvent("e1", "🎵", "Live Jazz · Алатау Бар", "Музыка", "20:00", 42, isLive = true, isFree = false, price = "2 000 ₸"),
    MapEvent("e2", "🛍", "Маркет «Алматы Craft»", "Маркет", "11:00–20:00", 128, isFree = true),
    MapEvent("e3", "🎨", "Выставка Amin Art", "Арт", "12:00–21:00", 74, isFree = true),
    MapEvent("e4", "🎤", "Стендап Ночь", "Стендап", "21:00", 91, price = "3 500 ₸"),
    MapEvent("e5", "🏋️", "Парковая тренировка", "Спорт", "07:00", 35, isFree = true),
    MapEvent("e6", "🎬", "Кино на крыше", "Кино", "22:00", 60, price = "1 500 ₸"),
)

private val mapCategories = listOf(
    "Все", "🎵 Музыка", "🎨 Арт", "🛍 Маркет", "🏋️ Спорт", "🎤 Стендап", "🎬 Кино",
)

// ─────────────────────────────────────────────────────────────────────────────
//  C1: MapScreen
// ─────────────────────────────────────────────────────────────────────────────

class MapScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedCategory by remember { mutableStateOf(0) }
        var sheetExpanded by remember { mutableStateOf(false) }
        var showCluster by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(NightBackground)) {

            // ── Заглушка карты ──────────────────────────────────────────
            MapPlaceholder(
                modifier = Modifier.fillMaxSize(),
                onPinClick = { sheetExpanded = true },
                onClusterClick = { showCluster = true },
            )

            // ── Категории (сверху) ──────────────────────────────────────
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.height(52.dp))
                // Поисковая строка
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(NightSurface.copy(alpha = 0.94f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔍", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Поиск событий...", fontSize = 14.sp, color = NightTextSecondary)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("⚙️", fontSize = 18.sp) }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(mapCategories.indices.toList()) { i ->
                        val sel = i == selectedCategory
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (sel) Brush.linearGradient(BrandGradient)
                                    else Brush.linearGradient(listOf(NightSurface.copy(alpha = 0.94f), NightSurface.copy(alpha = 0.94f))),
                                )
                                .border(1.dp, if (!sel) Color.White.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(999.dp))
                                .clickableNoRipple { selectedCategory = i }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(mapCategories[i], fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }
            }

            // ── FAB Live ────────────────────────────────────────────────
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (sheetExpanded) 340.dp else 120.dp)
                    .size(52.dp).clip(CircleShape).background(Brush.linearGradient(LiveGradient))
                    .clickableNoRipple { nav.push(LiveMapScreen()) },
                contentAlignment = Alignment.Center,
            ) { Text("🔴", fontSize = 18.sp) }

            // ── FAB Локация ─────────────────────────────────────────────
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (sheetExpanded) 400.dp else 180.dp)
                    .size(46.dp).clip(CircleShape).background(NightSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) { Text("📍", fontSize = 18.sp) }

            // ── Bottom Sheet: список событий ────────────────────────────
            EventBottomSheet(
                modifier = Modifier.align(Alignment.BottomCenter),
                events = sampleEvents,
                expanded = sheetExpanded,
                onToggle = { sheetExpanded = !sheetExpanded },
                onEventClick = {},
            )

            // ── Cluster popup ───────────────────────────────────────────
            if (showCluster) {
                EventClusterSheet(
                    events = sampleEvents.take(3),
                    onDismiss = { showCluster = false },
                    onEventClick = {},
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Заглушка карты
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapPlaceholder(modifier: Modifier, onPinClick: () -> Unit, onClusterClick: () -> Unit) {
    Box(modifier.background(Color(0xFF0D1F2D))) {
        // Сетка улиц
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(8) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.04f)))
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(5) {
                Box(Modifier.width(1.dp).fillMaxSize().background(Color.White.copy(alpha = 0.04f)))
            }
        }

        // Пины
        Box(Modifier.align(Alignment.Center).offset((-40).dp, (-60).dp)) {
            EventPin("🎵", Violet600, onClick = onPinClick)
        }
        Box(Modifier.align(Alignment.Center).offset(60.dp, 20.dp)) {
            EventPin("🎨", Color(0xFF5DCAA5), onClick = onPinClick)
        }
        Box(Modifier.align(Alignment.Center).offset((-20).dp, 80.dp)) {
            EventPin("🔴", Color(0xFFFF4757), isLive = true, onClick = onPinClick)
        }
        // Кластер
        Box(Modifier.align(Alignment.Center).offset(100.dp, (-40).dp)) {
            ClusterPin(count = 4, onClick = onClusterClick)
        }
    }
}

@Composable
private fun EventPin(emoji: String, color: Color, isLive: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.90f))
                .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
        Box(Modifier.size(width = 2.dp, height = 8.dp).background(color.copy(alpha = 0.7f)))
    }
}

@Composable
private fun ClusterPin(count: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(Brush.linearGradient(BrandGradient))
            .border(2.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text("$count", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventBottomSheet(modifier: Modifier = Modifier, events: List<MapEvent>, expanded: Boolean, onToggle: () -> Unit, onEventClick: (MapEvent) -> Unit) {
    Box(
        modifier.fillMaxWidth().height(if (expanded) 400.dp else 100.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(NightSurface),
    ) {
        Column {
            // Дескриптор
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickableNoRipple(onToggle), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.20f)))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${events.size} событий рядом", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(if (expanded) "Карта" else "Список", fontSize = 13.sp, color = Violet400)
            }

            if (expanded) {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events) { event ->
                        EventListRow(event, onClick = { onEventClick(event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventListRow(event: MapEvent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f)).clickableNoRipple(onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (event.isLive) Brush.linearGradient(LiveGradient) else Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) { Text(event.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (event.isLive) {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Live.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("LIVE", fontSize = 9.sp, color = Live, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🕐 ${event.time}", fontSize = 11.sp, color = NightTextSecondary)
                Text("  ·  ", fontSize = 11.sp, color = NightTextTertiary)
                Text("👥 ${event.attending}", fontSize = 11.sp, color = NightTextSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(if (event.isFree) "Бесплатно" else event.price, fontSize = 12.sp, color = if (event.isFree) Soft else Violet400, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C2: EventClusterSheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventClusterSheet(events: List<MapEvent>, onDismiss: () -> Unit, onEventClick: (Any) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple(onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(NightSurface)
                .padding(bottom = 32.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.20f)))
            }
            Text("${events.size} события рядом", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
            events.forEach { event ->
                EventListRow(event, onClick = { onEventClick(event) })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C2.0: LiveMapScreen
// ─────────────────────────────────────────────────────────────────────────────

class LiveMapScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val liveEvents = sampleEvents.filter { it.isLive }
        val onlineCount = 247

        Box(Modifier.fillMaxSize().background(Color(0xFF060D14))) {
            // Карта с красными пульсирующими пинами
            Box(Modifier.fillMaxSize().background(Color(0xFF070F18))) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    repeat(8) { Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.03f))) }
                }

                // Live пин
                Box(Modifier.align(Alignment.Center).offset((-30).dp, (-40).dp)) {
                    LivePinComposable("🎵")
                }
                Box(Modifier.align(Alignment.Center).offset(70.dp, 30.dp)) {
                    LivePinComposable("🎤")
                }
            }

            // Хедер
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple { nav.pop() },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Живая карта", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("$onlineCount человек онлайн сейчас", fontSize = 11.sp, color = NightTextSecondary)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Live.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Live))
                            Spacer(Modifier.width(5.dp))
                            Text("LIVE", fontSize = 11.sp, color = Live, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Нижняя панель
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(NightSurface)
                    .padding(16.dp),
            ) {
                Text("СЕЙЧАС ПРОИСХОДИТ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 10.dp))
                liveEvents.forEach { event ->
                    EventListRow(event, onClick = {})
                    Spacer(Modifier.height(8.dp))
                }
                // Пульс активности
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Info.copy(alpha = 0.08f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Алматы · пиковая активность 20:00–23:00", fontSize = 12.sp, color = Info)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LivePinComposable(emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Пульс
            Box(Modifier.size(60.dp).clip(CircleShape).background(Live.copy(alpha = 0.10f)))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Live.copy(alpha = 0.90f))
                .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp) }
        }
        Box(Modifier.size(width = 2.dp, height = 8.dp).background(Live.copy(alpha = 0.7f)))
    }
}

private fun Modifier.offset(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp): Modifier =
    this.padding(start = if (x > 0.dp) x else 0.dp, top = if (y > 0.dp) y else 0.dp,
        end = if (x < 0.dp) -x else 0.dp, bottom = if (y < 0.dp) -y else 0.dp)

private val Live = Color(0xFFFF4757)
