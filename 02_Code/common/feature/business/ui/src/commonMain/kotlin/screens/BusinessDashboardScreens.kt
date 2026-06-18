package com.eventmap.business.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Бизнес-дашборд — экраны организатора (M7-M9)
//  BusinessOrganizerScreen  — главный дашборд организатора
//  BusinessEventListScreen  — список событий организатора
//  BusinessAnalyticsScreen  — детальная аналитика события
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)
private val Info = Color(0xFF85B7EB)

// ─────────────────────────────────────────────────────────────────────────────
//  Модели
// ─────────────────────────────────────────────────────────────────────────────

private data class OrgEvent(
    val id: String,
    val emoji: String,
    val title: String,
    val date: String,
    val sold: Int,
    val total: Int,
    val revenue: String,
    val status: OrgEventStatus,
)

private enum class OrgEventStatus { DRAFT, PUBLISHED, LIVE, FINISHED }

private val sampleOrgEvents = listOf(
    OrgEvent("e1", "🎵", "Jazz Квартал · Весенний сезон", "Пт, 14 июн · 20:00", 234, 300, "468 000 ₸", OrgEventStatus.PUBLISHED),
    OrgEvent("e2", "🎨", "Арт-маркет «Апрель»", "Сб, 15 июн · 11:00", 831, 1000, "1 662 000 ₸", OrgEventStatus.LIVE),
    OrgEvent("e3", "🎤", "Стендап-вечер: Новые голоса", "Вс, 16 июн · 19:00", 0, 150, "—", OrgEventStatus.DRAFT),
    OrgEvent("e4", "🎬", "Кинопоказ: Казахское кино", "Вт, 11 июн · 20:30", 120, 120, "240 000 ₸", OrgEventStatus.FINISHED),
)

// ─────────────────────────────────────────────────────────────────────────────
//  M7 · BusinessOrganizerScreen — дашборд организатора
// ─────────────────────────────────────────────────────────────────────────────

class BusinessOrganizerScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedPeriod by remember { mutableStateOf(1) }
        val periods = listOf("Неделя", "Месяц", "Год")

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 20.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Дашборд организатора", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("AlmatyConcerts", fontSize = 12.sp, color = NightTextSecondary)
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("🏢", fontSize = 16.sp) }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Period selector
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    periods.forEachIndexed { i, p ->
                        val sel = i == selectedPeriod
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                .border(1.dp, if (!sel) Color.White.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(999.dp))
                                .clickableNoRipple { selectedPeriod = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(p, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }

                // Summary cards
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryCard(Modifier.weight(1f), "💰", "Выручка", "2 370 000 ₸", "+14%", true)
                    SummaryCard(Modifier.weight(1f), "🎟", "Продано", "1 185", "+8%", true)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryCard(Modifier.weight(1f), "👁", "Просмотров", "47 230", "+22%", true)
                    SummaryCard(Modifier.weight(1f), "⭐", "Рейтинг", "4.8 / 5", "+0.2", true)
                }

                // График (упрощённый)
                DashLabel("ПРОДАЖИ · ЗА МЕСЯЦ", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp))
                BarChart(
                    Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 20.dp),
                    values = listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.8f, 0.55f, 0.9f, 0.65f, 0.75f, 0.85f, 1.0f),
                    labels = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "", "", "", "", "", ""),
                )

                // Мои события
                DashLabel("МОИ СОБЫТИЯ", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp))
                sampleOrgEvents.forEach { event ->
                    OrgEventRow(event) {
                        nav.push(BusinessAnalyticsScreen(event.id, event.title, event.emoji))
                    }
                }

                // Кнопка создать
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.push(BusinessEventListScreen()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("＋", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Создать событие", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, emoji: String, label: String, value: String, change: String, positive: Boolean) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = NightTextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(change, fontSize = 11.sp, color = if (positive) Soft else Coral500, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarChart(modifier: Modifier, values: List<Float>, labels: List<String>) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier.fillMaxWidth().fillMaxSize(v).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Brush.linearGradient(BrandGradient, start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY), end = androidx.compose.ui.geometry.Offset(0f, 0f))),
                )
                if (labels.getOrNull(i)?.isNotEmpty() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(labels[i], fontSize = 8.sp, color = NightTextTertiary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun OrgEventRow(event: OrgEvent, onClick: () -> Unit) {
    val statusColor = when (event.status) {
        OrgEventStatus.LIVE -> Color(0xFFFF4757)
        OrgEventStatus.PUBLISHED -> Soft
        OrgEventStatus.DRAFT -> NightTextSecondary
        OrgEventStatus.FINISHED -> NightTextTertiary
    }
    val statusLabel = when (event.status) {
        OrgEventStatus.LIVE -> "🔴 Live"
        OrgEventStatus.PUBLISHED -> "✓ Опубликовано"
        OrgEventStatus.DRAFT -> "✏ Черновик"
        OrgEventStatus.FINISHED -> "✔ Завершено"
    }

    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(BrandGradient, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(48f, 48f))),
            contentAlignment = Alignment.Center,
        ) { Text(event.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(event.date, fontSize = 11.sp, color = NightTextSecondary)
            Spacer(Modifier.height(4.dp))
            // Прогресс продаж
            if (event.total > 0 && event.status != OrgEventStatus.DRAFT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { event.sold.toFloat() / event.total },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(999.dp)),
                        color = Violet600,
                        trackColor = Color.White.copy(alpha = 0.10f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${event.sold}/${event.total}", fontSize = 10.sp, color = NightTextSecondary)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(event.revenue, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) { Text(statusLabel, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DashLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = modifier)
}

// ─────────────────────────────────────────────────────────────────────────────
//  M8 · BusinessEventListScreen — все события организатора
// ─────────────────────────────────────────────────────────────────────────────

class BusinessEventListScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Активные", "Черновики", "Завершённые")

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Мои события", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple {}.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("＋ Создать", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }

            // Табы
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Violet600.copy(alpha = 0.20f) else Color.Transparent)
                            .border(1.dp, if (sel) Violet600.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text(t, fontSize = 12.sp, color = if (sel) Violet400 else NightTextSecondary) }
                }
            }

            val filtered = when (selectedTab) {
                0 -> sampleOrgEvents.filter { it.status == OrgEventStatus.PUBLISHED || it.status == OrgEventStatus.LIVE }
                1 -> sampleOrgEvents.filter { it.status == OrgEventStatus.DRAFT }
                else -> sampleOrgEvents.filter { it.status == OrgEventStatus.FINISHED }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Нет событий", fontSize = 16.sp, color = NightTextSecondary)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(filtered) { event ->
                        OrgEventRow(event) { nav.push(BusinessAnalyticsScreen(event.id, event.title, event.emoji)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M9 · BusinessAnalyticsScreen — аналитика конкретного события
// ─────────────────────────────────────────────────────────────────────────────

data class BusinessAnalyticsScreen(
    val eventId: String,
    val title: String,
    val emoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        // sample data
        val ageGroups = listOf("18-24" to 0.38f, "25-34" to 0.42f, "35-44" to 0.14f, "45+" to 0.06f)
        val sources = listOf("Лента" to 0.45f, "Поиск" to 0.28f, "Карта" to 0.15f, "Друзья" to 0.12f)
        val hourSales = listOf(0.1f, 0.15f, 0.35f, 0.6f, 0.8f, 0.7f, 0.55f, 0.4f, 0.3f, 0.2f, 0.15f, 0.1f)

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Аналитика", fontSize = 14.sp, color = NightTextSecondary)
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

                // KPI row
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(Modifier.weight(1f), "🎟", "Продано", "234 / 300", Violet400)
                    KpiCard(Modifier.weight(1f), "💰", "Выручка", "468 000 ₸", Soft)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(Modifier.weight(1f), "👁", "Просмотры", "12 450", Info)
                    KpiCard(Modifier.weight(1f), "🔗", "Конверсия", "1.88%", Coral500)
                }

                // Продажи по часам
                DashLabel("ПРОДАЖИ ПО ЧАСАМ", Modifier.padding(top = 20.dp, bottom = 10.dp))
                Box(Modifier.fillMaxWidth().height(90.dp)) {
                    BarChart(Modifier.fillMaxSize(), hourSales, List(12) { if (it % 3 == 0) "${8 + it * 2}:00" else "" })
                }

                // Возраст аудитории
                DashLabel("АУДИТОРИЯ · ВОЗРАСТ", Modifier.padding(top = 20.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ageGroups.forEach { (label, frac) ->
                        AudienceBar(label, frac, Violet600)
                    }
                }

                // Источники трафика
                DashLabel("ИСТОЧНИКИ ТРАФИКА", Modifier.padding(top = 20.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { (label, frac) ->
                        AudienceBar(label, frac, Coral500)
                    }
                }

                // Действия
                DashLabel("ДЕЙСТВИЯ", Modifier.padding(top = 20.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("✏️ Редактировать событие", false) {}
                    ActionButton("📤 Поделиться аналитикой", false) {}
                    ActionButton("🗑 Удалить событие", true) {}
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun KpiCard(modifier: Modifier, emoji: String, label: String, value: String, valueColor: Color) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(14.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = NightTextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

@Composable
private fun AudienceBar(label: String, fraction: Float, barColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.width(60.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(barColor))
        }
        Spacer(Modifier.width(8.dp))
        Text("${(fraction * 100).toInt()}%", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun ActionButton(label: String, danger: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface)
            .border(1.dp, if (danger) Color(0xFFE24B4A).copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = if (danger) Color(0xFFE24B4A) else Color.White, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = if (danger) Color(0xFFE24B4A).copy(alpha = 0.6f) else NightTextSecondary)
    }
}
