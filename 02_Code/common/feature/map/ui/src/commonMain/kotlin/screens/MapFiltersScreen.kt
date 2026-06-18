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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  MapFiltersScreen — расширенные фильтры карты
// =====================================================================

private val filterCategories = listOf(
    "🎵" to "Музыка", "🎨" to "Арт", "🛍" to "Маркет", "🏋️" to "Спорт",
    "🎤" to "Стендап", "🎬" to "Кино", "☕" to "Встреча", "🎮" to "Игры",
    "🌿" to "Природа", "📚" to "Лекции", "🍕" to "Еда", "🎭" to "Театр",
)

private val timeSlots = listOf("Сейчас", "Сегодня", "Завтра", "Эти выходные", "Эта неделя")
private val priceFilters = listOf("Все", "Бесплатно", "До 2 000 ₸", "До 5 000 ₸", "5 000+ ₸")

class MapFiltersScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        // Состояние фильтров
        val selectedCategories = remember { mutableStateOf(setOf<Int>()) }
        var selectedTime by remember { mutableStateOf(0) }
        var selectedPrice by remember { mutableStateOf(0) }
        var radiusKm by remember { mutableStateOf(3f) }
        var showFriendsOnly by remember { mutableStateOf(false) }
        var showLiveOnly by remember { mutableStateOf(false) }
        var showVerifiedOnly by remember { mutableStateOf(false) }

        val activeCount = selectedCategories.value.size +
            (if (selectedTime != 0) 1 else 0) +
            (if (selectedPrice != 0) 1 else 0) +
            (if (showFriendsOnly) 1 else 0) +
            (if (showLiveOnly) 1 else 0) +
            (if (showVerifiedOnly) 1 else 0)

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple { nav.pop() }) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(NightBackground)
                    .clickableNoRipple { /* consume */ }
                    .verticalScroll(rememberScrollState()),
            ) {
                // Хедер
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Фильтры", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                    if (activeCount > 0) {
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.20f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("$activeCount активных", fontSize = 12.sp, color = Violet400) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {
                            selectedCategories.value = emptySet()
                            selectedTime = 0; selectedPrice = 0
                            showFriendsOnly = false; showLiveOnly = false; showVerifiedOnly = false
                            radiusKm = 3f
                        }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("Сбросить", fontSize = 12.sp, color = NightTextSecondary) }
                }

                // Категории
                SHeader("КАТЕГОРИЯ")
                val rows = filterCategories.chunked(4)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEachIndexed { ri, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { ci, (emoji, label) ->
                                val i = ri * 4 + ci
                                val sel = i in selectedCategories.value
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                                        .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickableNoRipple { selectedCategories.value = if (sel) selectedCategories.value - i else selectedCategories.value + i }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                    Text(label, fontSize = 10.sp, color = if (sel) Violet400 else NightTextSecondary, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                }

                // Время
                SHeader("КОГДА")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(timeSlots.indices.toList()) { i ->
                        val sel = i == selectedTime
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                                .clickableNoRipple { selectedTime = i }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(timeSlots[i], fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }

                // Цена
                SHeader("ЦЕНА")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(priceFilters.indices.toList()) { i ->
                        val sel = i == selectedPrice
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                                .clickableNoRipple { selectedPrice = i }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(priceFilters[i], fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }

                // Радиус
                SHeader("РАДИУС · ${radiusKm.toInt()} км")
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Slider(
                        value = radiusKm,
                        onValueChange = { radiusKm = it },
                        valueRange = 1f..50f,
                        steps = 48,
                        colors = SliderDefaults.colors(
                            thumbColor = Violet600, activeTrackColor = Violet600, inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1 км", fontSize = 10.sp, color = NightTextSecondary)
                        Text("50 км", fontSize = 10.sp, color = NightTextSecondary)
                    }
                }

                // Переключатели
                SHeader("ДОПОЛНИТЕЛЬНО")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchFilterRow("👥 Куда идут друзья", showFriendsOnly) { showFriendsOnly = !showFriendsOnly }
                    SwitchFilterRow("🔴 Только Live-события", showLiveOnly) { showLiveOnly = !showLiveOnly }
                    SwitchFilterRow("✓ Только верифицированные", showVerifiedOnly) { showVerifiedOnly = !showVerifiedOnly }
                }

                Spacer(Modifier.height(16.dp))

                // Применить
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (activeCount > 0) "Применить ($activeCount)" else "Показать события",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SHeader(text: String) {
    Text(
        text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchFilterRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Violet600,
                uncheckedThumbColor = NightTextSecondary, uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
            ),
        )
    }
}
