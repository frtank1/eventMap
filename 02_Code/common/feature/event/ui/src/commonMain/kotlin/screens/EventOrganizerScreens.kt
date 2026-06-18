package com.eventmap.event.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
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
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  EventQRScreen      — QR-код события для организатора (сканируют на входе)
//  LiveStreamScreen   — запустить/управлять Live-трансляцией
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Live = Color(0xFFFF4757)
private val Coral500 = Color(0xFFFF5C8A)

// ─────────────────────────────────────────────────────────────────────────────
//  EventQRScreen — QR для организатора
// ─────────────────────────────────────────────────────────────────────────────

data class EventQRScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
    val date: String = "Пт, 14 июн 2026 · 20:00",
    val venue: String = "Алатау Бар",
    val totalTickets: Int = 300,
    val scanned: Int = 87,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var scanCount by remember { mutableStateOf(scanned) }

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
                    Text("QR-код организатора", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(eventTitle, fontSize = 11.sp, color = NightTextSecondary, maxLines = 1)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {}.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("📤", fontSize = 14.sp) }
            }

            Column(Modifier.weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // QR блок
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(eventEmoji, fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(eventTitle, fontSize = 13.sp, color = Color(0xFF0A1420), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Text(date, fontSize = 10.sp, color = Color(0xFF56657A))
                        Spacer(Modifier.height(16.dp))
                        // QR пиксели
                        OrgQrPattern()
                        Spacer(Modifier.height(12.dp))
                        Text("EVT-${eventId.uppercase()}", fontSize = 11.sp, color = Color(0xFF56657A), letterSpacing = 2.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Счётчик сканирований
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScanStat("✅", "$scanCount", "Отсканировано")
                    Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.08f)))
                    ScanStat("🎟", "$totalTickets", "Всего билетов")
                    Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.08f)))
                    ScanStat("⏳", "${totalTickets - scanCount}", "Не пришли")
                }

                Spacer(Modifier.height(12.dp))

                // Прогресс посещения
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Заполнение зала", fontSize = 12.sp, color = NightTextSecondary)
                        Text("${(scanCount.toFloat() / totalTickets * 100).toInt()}%", fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth(scanCount.toFloat() / totalTickets).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient)))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Симулировать скан
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(Soft.copy(alpha = 0.15f)).border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                        .clickableNoRipple { if (scanCount < totalTickets) scanCount++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📷", fontSize = 16.sp)
                        Text("Сканировать билет", color = Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgQrPattern() {
    val pattern = listOf(
        listOf(1,1,1,0,1,1,1,0,1),
        listOf(1,0,1,0,0,1,0,1,0),
        listOf(1,0,1,1,0,0,1,0,1),
        listOf(0,1,0,1,1,0,1,1,0),
        listOf(1,1,1,0,0,1,1,0,1),
        listOf(0,1,0,0,1,0,0,1,1),
        listOf(1,0,1,1,0,1,0,0,1),
        listOf(1,1,0,0,1,1,1,0,0),
        listOf(1,0,1,0,0,1,0,1,1),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        pattern.forEach { row ->
            Row {
                row.forEach { cell ->
                    Box(Modifier.size(12.dp).padding(1.dp).clip(RoundedCornerShape(2.dp)).background(if (cell == 1) Color(0xFF0A1420) else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun ScanStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 9.sp, color = NightTextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LiveStreamScreen — управление Live-трансляцией
// ─────────────────────────────────────────────────────────────────────────────

data class LiveStreamScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var isLive by remember { mutableStateOf(false) }
        var viewers by remember { mutableStateOf(0) }
        var reactions by remember { mutableStateOf(0) }

        val pulse = rememberInfiniteTransition(label = "live")
        val pulseAlpha by pulse.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "alpha",
        )

        Box(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF1A0010), Color(0xFF0A1420)))),
        ) {
            // Превью камеры
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(eventEmoji, fontSize = 100.sp)
                    Text("Камера", fontSize = 14.sp, color = Color.White.copy(alpha = 0.35f))
                }
            }

            // Хедер
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 52.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))

                if (isLive) {
                    // LIVE бейдж
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Live.copy(alpha = pulseAlpha)).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Spacer(Modifier.width(5.dp))
                        Text("LIVE", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    // Зрители
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("👁", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("$viewers", fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.width(8.dp))
                // Сменить камеру
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple {}, contentAlignment = Alignment.Center) {
                    Text("🔄", fontSize = 16.sp)
                }
            }

            // Боковые реакции (если live)
            if (isLive) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf("❤️", "🔥", "😍", "👏").forEach { emoji ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f)).clickableNoRipple { reactions++ },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 20.sp) }
                    }
                }
            }

            // Нижняя панель
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                if (!isLive) {
                    // Настройки перед стартом
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(eventTitle, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Готово к трансляции", fontSize = 11.sp, color = Soft)
                        }
                        Text(eventEmoji, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Live, Coral500)))
                            .clickableNoRipple { isLive = true; viewers = 0 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                            Text("Начать трансляцию", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    // Статистика в реальном времени
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LiveStat(Modifier.weight(1f), "👁", "$viewers", "зрителей")
                        LiveStat(Modifier.weight(1f), "🔥", "$reactions", "реакций")
                        LiveStat(Modifier.weight(1f), "⏱", "00:${if (viewers < 10) "0$viewers" else "$viewers"}", "время")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Симулировать зрителей
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .clickableNoRipple { viewers += (5..30).random() },
                            contentAlignment = Alignment.Center,
                        ) { Text("+ Зрители", color = NightTextSecondary, fontSize = 13.sp) }

                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                .background(Live.copy(alpha = 0.20f)).border(1.dp, Live.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                .clickableNoRipple { isLive = false },
                            contentAlignment = Alignment.Center,
                        ) { Text("⏹ Завершить", color = Live, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStat(modifier: Modifier, emoji: String, value: String, label: String) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 9.sp, color = NightTextSecondary)
    }
}
