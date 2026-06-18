package com.eventmap.video.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  VideoEditScreen — обрезка, фильтры, подпись перед публикацией
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)

private data class VideoFilter(val name: String, val tint: Color)

private val filters = listOf(
    VideoFilter("Нет", Color.Transparent),
    VideoFilter("Ночь", Color(0xFF0A1420).copy(alpha = 0.40f)),
    VideoFilter("Тепло", Color(0xFFFF8C42).copy(alpha = 0.25f)),
    VideoFilter("Холод", Color(0xFF42A5F5).copy(alpha = 0.25f)),
    VideoFilter("Вспышка", Color(0xFFFFFFFF).copy(alpha = 0.15f)),
    VideoFilter("Noir", Color(0xFF000000).copy(alpha = 0.35f)),
)

private val tools = listOf("✂️" to "Обрезка", "🎵" to "Музыка", "📝" to "Текст", "😊" to "Стикеры", "🎨" to "Фильтр")

class VideoEditScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedFilter by remember { mutableStateOf(0) }
        var selectedTool by remember { mutableStateOf(-1) }
        var trimStart by remember { mutableStateOf(0f) }
        var trimEnd by remember { mutableStateOf(1f) }
        var caption by remember { mutableStateOf("") }
        var linkedEvent by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Color.White, fontSize = 14.sp) }
                Spacer(Modifier.weight(1f))
                Text("Редактор", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.pop() }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Далее →", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }

            // Превью видео
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0A1420)))),
                contentAlignment = Alignment.Center,
            ) {
                // Фильтр-оверлей
                if (filters[selectedFilter].tint != Color.Transparent) {
                    Box(Modifier.fillMaxSize().background(filters[selectedFilter].tint))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 64.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Jazz Квартал", fontSize = 16.sp, color = Color.White.copy(alpha = 0.70f))
                    if (caption.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.50f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(caption, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold) }
                    }
                }
                // Таймер
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("0:${(trimEnd * 30).toInt()}s", fontSize = 11.sp, color = Color.White)
                }
                // Play кнопка
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("▶", fontSize = 20.sp, color = Color.White) }
            }

            Spacer(Modifier.height(10.dp))

            // Тримминг
            Column(Modifier.padding(horizontal = 16.dp)) {
                // Полоса кадров
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(NightSurface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Кадры-превью
                    repeat(12) { i ->
                        val frameAlpha = when {
                            i.toFloat() / 12 < trimStart -> 0.25f
                            i.toFloat() / 12 > trimEnd -> 0.25f
                            else -> 1f
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF1A0A2E).copy(alpha = frameAlpha),
                                            Color(0xFF2D1B69).copy(alpha = frameAlpha * 0.7f),
                                        )
                                    )
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Слайдер обрезки
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${(trimStart * 30).toInt()}s", fontSize = 10.sp, color = NightTextTertiary, modifier = Modifier.width(24.dp))
                    Slider(
                        value = trimEnd,
                        onValueChange = { trimEnd = maxOf(it, trimStart + 0.1f) },
                        modifier = Modifier.weight(1f).height(20.dp),
                        colors = SliderDefaults.colors(thumbColor = Violet600, activeTrackColor = Violet600, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
                    )
                    Text("${(trimEnd * 30).toInt()}s", fontSize = 10.sp, color = NightTextTertiary, modifier = Modifier.width(24.dp))
                }
            }

            // Инструменты
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tools.forEachIndexed { i, (emoji, label) ->
                    val sel = i == selectedTool
                    Column(
                        Modifier.clickableNoRipple { selectedTool = if (selectedTool == i) -1 else i },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                .border(1.dp, if (!sel) Color.White.copy(alpha = 0.10f) else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 18.sp) }
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 9.sp, color = if (sel) Violet400 else NightTextSecondary)
                    }
                }
            }

            // Фильтры (если выбран инструмент Фильтр)
            if (selectedTool == 4) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(filters) { i, filter ->
                        val sel = i == selectedFilter
                        Column(
                            Modifier.clickableNoRipple { selectedFilter = i },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF2D1B69))))
                                    .border(2.dp, if (sel) Violet600 else Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            ) {
                                if (filter.tint != Color.Transparent) Box(Modifier.fillMaxSize().background(filter.tint))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(filter.name, fontSize = 9.sp, color = if (sel) Violet400 else NightTextSecondary)
                        }
                    }
                }
            }

            // Подпись + событие
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(NightSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(caption.ifEmpty { "Добавить подпись..." }, fontSize = 13.sp, color = if (caption.isEmpty()) NightTextSecondary else Color.White) }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NightSurface)
                        .border(1.dp, if (linkedEvent) Violet600.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickableNoRipple { linkedEvent = !linkedEvent }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (linkedEvent) "🎵" else "🔗", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (linkedEvent) "Jazz Квартал · Весенний сезон" else "Привязать событие", fontSize = 12.sp, color = if (linkedEvent) Violet400 else NightTextSecondary, modifier = Modifier.weight(1f))
                    if (linkedEvent) Text("✓", fontSize = 14.sp, color = Violet400)
                }
            }

            // Опубликовать
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(50.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.pop() },
                contentAlignment = Alignment.Center,
            ) { Text("Опубликовать видео", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
