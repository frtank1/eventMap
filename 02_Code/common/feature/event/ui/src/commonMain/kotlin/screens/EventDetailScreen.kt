package com.eventmap.event.ui.screens

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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Coral500
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
//  D · Событие-рилс
//  D1 EventFeedScreen  — вертикальный пейджер событий (reel-style)
//  D2 CreateEventScreen — создание события
//  D3 EventAttendeesScreen — список участников
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class EventItem(
    val id: String,
    val emoji: String,
    val title: String,
    val organizer: String,
    val location: String,
    val date: String,
    val price: String,
    val attending: Int,
    val likes: Int,
    val comments: Int,
    val isLive: Boolean = false,
    val isFree: Boolean = false,
    val tags: List<String> = emptyList(),
)

private val sampleFeedEvents = listOf(
    EventItem("d1", "🎵", "Jazz Квартал · Весенний сет", "Алатау Бар", "ул. Желтоксан 111", "Пт, 14 июн · 20:00",
        "2 000 ₸", 184, 432, 67, isLive = true, tags = listOf("Джаз", "18+", "Живая музыка")),
    EventItem("d2", "🛍", "Craft Маркет «Весна»", "EventMap KZ", "EXPO Astana", "Сб-Вс · 11:00–20:00",
        "Бесплатно", 831, 1200, 94, isFree = true, tags = listOf("Маркет", "Хэндмейд", "Семья")),
    EventItem("d3", "🎤", "Стендап Ночь: Новые лица", "Comedy Club KZ", "Theatre Club", "Сб · 21:00",
        "3 500 ₸", 112, 305, 44, tags = listOf("Стендап", "18+")),
    EventItem("d4", "🎨", "Арт-резиденция Amin", "Amin Gallery", "Галерея Центр", "14–30 июн",
        "Бесплатно", 290, 780, 112, isFree = true, tags = listOf("Арт", "Выставка")),
)

// ─────────────────────────────────────────────────────────────────────────────
//  D1: EventFeedScreen (рилс-стиль)
// ─────────────────────────────────────────────────────────────────────────────

class EventFeedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val pager = rememberPagerState { sampleFeedEvents.size }

        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            EventPage(event = sampleFeedEvents[page], onAttend = {}, onShare = {},
                onShowAttendees = { nav.push(EventAttendeesV2Screen(sampleFeedEvents[page].id)) })
        }
    }
}

@Composable
private fun EventPage(event: EventItem, onAttend: () -> Unit, onShare: () -> Unit, onShowAttendees: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1520), NightBackground))),
    ) {
        // Основной контент
        Column(
            Modifier.fillMaxSize().padding(bottom = 120.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Теги
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(event.tags) { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(tag, fontSize = 11.sp, color = Color.White) }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    // LIVE badge
                    if (event.isLive) {
                        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Live))
                            Spacer(Modifier.width(5.dp))
                            Text("ИДЁТ СЕЙЧАС", fontSize = 11.sp, color = Live, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                        }
                    }
                    Text(event.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(event.emoji, fontSize = 11.sp) }
                        Spacer(Modifier.width(6.dp))
                        Text(event.organizer, fontSize = 13.sp, color = Violet400, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(event.location, fontSize = 12.sp, color = NightTextSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗓", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(event.date, fontSize = 12.sp, color = NightTextSecondary)
                    }
                }

                // Правая колонка реакций
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ReactionBtn("❤️", event.likes.toString(), onClick = {})
                    ReactionBtn("💬", event.comments.toString(), onClick = {})
                    ReactionBtn("👥", event.attending.toString(), onClick = onShowAttendees)
                    ReactionBtn("↗️", "", onClick = onShare)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Кнопка записи
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(if (event.isLive) LiveGradient else BrandGradient))
                        .clickableNoRipple(onAttend),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (event.isFree) "✓ Иду бесплатно" else "Купить · ${event.price}", color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("🔖", fontSize = 20.sp) }
            }
        }

        // Большой emoji-превью по центру сверху
        Box(
            Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center,
        ) { Text(event.emoji, fontSize = 100.sp, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ReactionBtn(emoji: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickableNoRipple(onClick)) {
        Text(emoji, fontSize = 26.sp)
        if (label.isNotEmpty()) Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D2: CreateEventScreen
// ─────────────────────────────────────────────────────────────────────────────

private val eventCategories = listOf(
    "🎵" to "Музыка", "🎨" to "Арт", "🛍" to "Маркет", "🏋️" to "Спорт",
    "🎤" to "Стендап", "🎬" to "Кино", "☕" to "Встреча", "🎮" to "Игры",
)

/** D2 v2 — полностью переработанный экран создания события */
class CreateEventV2Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf<Int?>(null) }
        var isFree by remember { mutableStateOf(true) }
        var price by remember { mutableStateOf("") }
        var step by remember { mutableStateOf(0) }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            // Топ-бар
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("Новое событие", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {}.padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Черновик", fontSize = 12.sp, color = NightTextSecondary) }
            }

            // Превью обложки
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selectedCategory != null) Brush.verticalGradient(listOf(Color(0xFF1A0D2A), NightBackground))
                        else Brush.verticalGradient(listOf(NightSurface, NightBackground))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedCategory?.let { eventCategories[it].first } ?: "🖼", fontSize = 44.sp)
                    Text(if (title.isNotBlank()) title else "Название события", fontSize = 16.sp,
                        color = if (title.isNotBlank()) Color.White else NightTextSecondary, fontWeight = FontWeight.Bold)
                    if (selectedCategory == null) Text("+ Выбери категорию ниже", fontSize = 11.sp, color = NightTextTertiary)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Категория
            SLabel("КАТЕГОРИЯ")
            val rows = eventCategories.chunked(4)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { ri, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { ci, (emoji, label) ->
                            val i = ri * 4 + ci
                            val sel = i == selectedCategory
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                                    .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickableNoRipple { selectedCategory = i }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(emoji, fontSize = 20.sp)
                                Text(label, fontSize = 10.sp, color = if (sel) Violet400 else NightTextSecondary, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SLabel("ОСНОВНОЕ")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") },
                    minLines = 3, maxLines = 5, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Место проведения") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))

            // Цена
            SLabel("СТОИМОСТЬ")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(4.dp),
            ) {
                listOf("Бесплатно", "Платное").forEachIndexed { i, label ->
                    val sel = (i == 0) == isFree
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickableNoRipple { isFree = i == 0 },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) Color.White else NightTextSecondary, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
            if (!isFree) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Цена, ₸") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
                        unfocusedTextColor = Color.White, focusedTextColor = Color.White,
                        unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(24.dp))

            val canPublish = title.length >= 3 && selectedCategory != null && location.isNotBlank()
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (canPublish) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("🚀 Опубликовать", color = if (canPublish) Color.White else NightTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
//  D3: EventAttendeesScreen
// ─────────────────────────────────────────────────────────────────────────────

data class EventAttendeesV2Screen(val eventId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val tabs = listOf("Идут (184)", "Хотят (56)", "Были раньше (320)")
        var selectedTab by remember { mutableStateOf(0) }

        val attendees = remember {
            List(10) { i ->
                Triple("Пользователь ${i + 1}", "@user${i + 1}", listOf("Алматы", "Астана", "Шымкент")[i % 3])
            }
        }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("Участники", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Табы
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tabs.indices.toList()) { i ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tabs[i], fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(attendees) { (name, handle, city) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
                            Text(name.take(1), fontSize = 16.sp, color = Violet400, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("$handle · $city", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {}.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) { Text("Подписаться", fontSize = 11.sp, color = Violet400) }
                    }
                }
            }
        }
    }
}

private val Live = Color(0xFFFF4757)
