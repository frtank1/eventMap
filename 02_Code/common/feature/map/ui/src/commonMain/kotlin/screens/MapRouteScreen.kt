package com.eventmap.map.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  MapRouteScreen — маршрут до события
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class RouteStep(val icon: String, val desc: String, val dist: String)

private val sampleSteps = listOf(
    RouteStep("⬆️", "Идите прямо по ул. Панфилова", "350 м"),
    RouteStep("↩️", "Поверните налево на пр. Абая", "120 м"),
    RouteStep("↪️", "Поверните направо на ул. Жибек жолы", "80 м"),
    RouteStep("📍", "Алатау Бар — слева", "Вы прибыли"),
)

data class MapRouteScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
    val venue: String = "Алатау Бар",
    val address: String = "ул. Жибек жолы, 50",
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedMode by remember { mutableStateOf(0) }
        val modes = listOf("🚶 Пешком" to "12 мин", "🚗 Авто" to "5 мин", "🚌 Транспорт" to "18 мин")

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
                Column(Modifier.weight(1f)) {
                    Text("Маршрут", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("$venue · $address", fontSize = 11.sp, color = NightTextSecondary)
                }
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("📤", fontSize = 16.sp) }
            }

            // Карта-заглушка
            Box(
                Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1F30)),
            ) {
                // Горизонтальные улицы
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    repeat(5) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                    }
                }
                // Маршрут — упрощённая линия
                Box(
                    Modifier.align(Alignment.Center).width(200.dp).height(100.dp),
                ) {
                    // Линия маршрута (горизонталь + вертикаль)
                    Box(Modifier.align(Alignment.BottomStart).width(140.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient)))
                    Box(Modifier.align(Alignment.BottomEnd).width(3.dp).height(70.dp).clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient)))
                    // Точка старт
                    Box(
                        Modifier.align(Alignment.BottomStart).size(14.dp).clip(CircleShape).background(Soft).border(2.dp, Color.White, CircleShape),
                    )
                    // Точка конец
                    Box(Modifier.align(Alignment.TopEnd)) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(eventEmoji, fontSize = 12.sp) }
                    }
                }
                // Подсказка расстояния
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("550 м · 12 мин пешком", fontSize = 11.sp, color = Color.White) }
            }

            Spacer(Modifier.height(10.dp))

            // Режимы транспорта
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modes.forEachIndexed { i, (label, time) ->
                    val sel = i == selectedMode
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickableNoRipple { selectedMode = i }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary)
                        Text(time, fontSize = 11.sp, color = if (sel) Color.White.copy(alpha = 0.80f) else NightTextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Шаги маршрута
            Row(Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                Text("МАРШРУТ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(sampleSteps.indices.toList()) { i ->
                    val step = sampleSteps[i]
                    val isLast = i == sampleSteps.lastIndex
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        // Иконка + вертикальная линия
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (isLast) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface))).border(1.dp, if (isLast) Color.Transparent else Color.White.copy(alpha = 0.10f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(step.icon, fontSize = 14.sp) }
                            if (!isLast) {
                                Box(Modifier.width(2.dp).height(28.dp).background(Color.White.copy(alpha = 0.08f)))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(step.desc, fontSize = 13.sp, color = if (isLast) Violet400 else Color.White, modifier = Modifier.weight(1f), fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal)
                            Text(step.dist, fontSize = 11.sp, color = NightTextSecondary)
                        }
                    }
                }
            }

            // Начать маршрут
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(54.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(BrandGradient)).clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🧭", fontSize = 18.sp)
                    Text("Начать маршрут", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
