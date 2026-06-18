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
//  EventCheckInScreen  — QR-сканирование / чекин
//  CheckInSuccessScreen — подтверждение чекина + XP-анимация
//  NearbyEventsScreen  — события рядом (< 500 м)
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Live = Color(0xFFFF4757)
private val Soft2 = Color(0xFF5DCAA5)

// ─────────────────────────────────────────────────────────────────────────────
//  EventCheckInScreen — QR-сканер
// ─────────────────────────────────────────────────────────────────────────────

data class EventCheckInScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var scanned by remember { mutableStateOf(false) }

        val pulse = rememberInfiniteTransition(label = "pulse")
        val pulseScale by pulse.animateFloat(
            initialValue = 1f, targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "scale",
        )

        Box(Modifier.fillMaxSize().background(NightBackground)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Хедер
                Row(
                    Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Чекин", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(eventTitle, fontSize = 12.sp, color = NightTextSecondary, maxLines = 1)
                    }
                    Text(eventEmoji, fontSize = 24.sp)
                }

                Spacer(Modifier.weight(1f))

                // QR-вьюфайндер
                Box(
                    Modifier.size(260.dp).scale(pulseScale),
                    contentAlignment = Alignment.Center,
                ) {
                    // Тёмный фон
                    Box(Modifier.size(240.dp).clip(RoundedCornerShape(20.dp)).background(NightSurface))

                    // Угловые рамки
                    val cornerColor = if (!scanned) Violet600 else Soft2
                    // Левый верхний
                    CornerBracket(Modifier.align(Alignment.TopStart), cornerColor, topLeft = true)
                    // Правый верхний
                    CornerBracket(Modifier.align(Alignment.TopEnd), cornerColor, topRight = true)
                    // Левый нижний
                    CornerBracket(Modifier.align(Alignment.BottomStart), cornerColor, bottomLeft = true)
                    // Правый нижний
                    CornerBracket(Modifier.align(Alignment.BottomEnd), cornerColor, bottomRight = true)

                    // Центр: иконка сканирования
                    if (!scanned) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 36.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Наведите на QR-код", fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
                            Text("организатора", fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Soft2.copy(alpha = 0.20f))
                                    .border(2.dp, Soft2.copy(alpha = 0.50f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", fontSize = 28.sp, color = Soft2, fontWeight = FontWeight.ExtraBold) }
                            Spacer(Modifier.height(10.dp))
                            Text("Код распознан!", fontSize = 14.sp, color = Soft2, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (!scanned) {
                    Text("или", fontSize = 13.sp, color = NightTextTertiary)
                    Spacer(Modifier.height(16.dp))
                    // Кнопка-симуляция сканирования
                    Box(
                        Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { scanned = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("Сканировать QR", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {},
                        contentAlignment = Alignment.Center,
                    ) { Text("Ввести код вручную", color = NightTextSecondary, fontSize = 14.sp) }
                } else {
                    Box(
                        Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple { nav.replace(CheckInSuccessScreen(eventId, eventTitle, eventEmoji)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Подтвердить чекин →", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CornerBracket(
    modifier: Modifier,
    color: Color,
    topLeft: Boolean = false,
    topRight: Boolean = false,
    bottomLeft: Boolean = false,
    bottomRight: Boolean = false,
) {
    val size = 28.dp
    val thick = 3.dp
    Box(modifier.padding(8.dp).size(size)) {
        if (topLeft || bottomLeft) Box(Modifier.width(thick).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(color))
        if (topRight || bottomRight) Box(Modifier.width(thick).fillMaxSize().align(Alignment.TopEnd).clip(RoundedCornerShape(999.dp)).background(color))
        if (topLeft || topRight) Box(Modifier.height(thick).fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(color))
        if (bottomLeft || bottomRight) Box(Modifier.height(thick).fillMaxWidth().align(Alignment.BottomStart).clip(RoundedCornerShape(999.dp)).background(color))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CheckInSuccessScreen — подтверждение
// ─────────────────────────────────────────────────────────────────────────────

data class CheckInSuccessScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        val bounce = rememberInfiniteTransition(label = "bounce")
        val emojiScale by bounce.animateFloat(
            initialValue = 1f, targetValue = 1.18f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "emoji",
        )

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Большое лого события
            Box(
                Modifier.size(110.dp).scale(emojiScale).clip(CircleShape)
                    .background(Brush.linearGradient(BrandGradient))
                    .border(3.dp, Soft2.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(eventEmoji, fontSize = 48.sp) }

            Spacer(Modifier.height(24.dp))
            Text("Вы отметились! 🎉", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(eventTitle, fontSize = 15.sp, color = NightTextSecondary, textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            // XP и бонусы
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NightSurface).padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                XpBonus("⚡", "+120 XP", "за чекин")
                Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.08f)))
                XpBonus("🔥", "x1.5", "бонус серии")
                Box(Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.08f)))
                XpBonus("🏅", "+1", "квест")
            }

            Spacer(Modifier.height(20.dp))

            // Достижение
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Soft2.copy(alpha = 0.10f))
                    .border(1.dp, Soft2.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🏆", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Новый бейдж разблокирован!", fontSize = 12.sp, color = Soft2, fontWeight = FontWeight.SemiBold)
                    Text("«Коллекционер» — 8 событий", fontSize = 11.sp, color = NightTextSecondary)
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.popUntilRoot() },
                contentAlignment = Alignment.Center,
            ) { Text("Отлично!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) { Text("Поделиться 📤", color = NightTextSecondary, fontSize = 13.sp) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun XpBonus(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, color = Violet400, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 10.sp, color = NightTextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  NearbyEventsScreen — события рядом
// ─────────────────────────────────────────────────────────────────────────────

private data class NearbyEvent(
    val id: String,
    val emoji: String,
    val title: String,
    val distance: String,
    val time: String,
    val category: String,
    val isLive: Boolean = false,
    val attendees: Int,
)

private val nearbyList = listOf(
    NearbyEvent("n1", "🎵", "Jazz Квартал · Весенний сезон", "80 м", "Сейчас", "Музыка", isLive = true, 234),
    NearbyEvent("n2", "☕", "Бизнес-нетворкинг «Утро»", "140 м", "10:00", "Встреча", attendees = 28),
    NearbyEvent("n3", "🎨", "Арт-воркшоп: Акварель", "210 м", "12:00", "Арт", attendees = 15),
    NearbyEvent("n4", "🛍", "Гаражная распродажа", "350 м", "11:00 – 18:00", "Маркет", attendees = 67),
    NearbyEvent("n5", "🧘", "Утренняя йога в парке", "420 м", "07:30", "Спорт", attendees = 12),
)

class NearbyEventsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

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
                    Text("Рядом с вами", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Алматы, Медеуский р-н · < 500 м", fontSize = 12.sp, color = NightTextSecondary)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple {}.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("🗺️ Карта", fontSize = 12.sp, color = NightTextSecondary) }
            }

            // Живое событие — банер
            nearbyList.firstOrNull { it.isLive }?.let { live ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF4757).copy(alpha = 0.20f), Violet600.copy(alpha = 0.20f))))
                        .border(1.dp, Color(0xFFFF4757).copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                        .clickableNoRipple {}.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(live.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Live))
                                Spacer(Modifier.width(5.dp))
                                Text("LIVE · ${live.distance}", fontSize = 11.sp, color = Live, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(live.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Text("${live.attendees} участников сейчас", fontSize = 11.sp, color = NightTextSecondary)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Live).padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("Войти", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nearbyList.filter { !it.isLive }) { event ->
                    NearbyEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun NearbyEventCard(event: NearbyEvent) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface)
            .clickableNoRipple {}.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) { Text(event.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍 ${event.distance}", fontSize = 11.sp, color = Violet400)
                Text("  ·  ", fontSize = 11.sp, color = NightTextTertiary)
                Text(event.time, fontSize = 11.sp, color = NightTextSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(event.category, fontSize = 10.sp, color = Violet400) }
            Spacer(Modifier.height(4.dp))
            Text("👥 ${event.attendees}", fontSize = 10.sp, color = NightTextSecondary)
        }
    }
}
