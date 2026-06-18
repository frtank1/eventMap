package com.eventmap.payment.ui.screens

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
import kotlin.time.Clock

// =====================================================================
//  Билеты
//  MyTicketsScreen    — список моих билетов (предстоящие / прошедшие)
//  TicketDetailScreen — детальный билет с QR-кодом
//  BookingSuccessScreen — экран подтверждения покупки
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)

private data class Ticket(
    val id: String,
    val emoji: String,
    val eventTitle: String,
    val venue: String,
    val date: String,
    val time: String,
    val seat: String,
    val price: String,
    val isPast: Boolean = false,
    val qrData: String = "EVT-${Clock.System.now().toEpochMilliseconds()}",
)

private val sampleTickets = listOf(
    Ticket("t1", "🎵", "Jazz Квартал · Весенний сезон", "Алатау Бар, Алматы", "Пт, 14 июн 2026", "20:00", "Партер · Ряд 3 · Место 14", "2 000 ₸"),
    Ticket("t2", "🎨", "Арт-маркет «Апрель»", "TSUM Almaty, 3 этаж", "Сб, 15 июн 2026", "11:00 – 20:00", "Свободный вход", "Бесплатно"),
    Ticket("t3", "🎤", "Comedy Club KZ · Новый сезон", "Театр им. Ауэзова", "Сб, 20 июн 2026", "19:00", "Балкон · Ряд 1 · Место 7", "3 500 ₸"),
    Ticket("t4", "🎬", "Кинопоказ: Казахское кино", "Кинотеатр Арман", "Вт, 11 июн 2026", "20:30", "Зал 2 · Ряд 5 · Место 9", "2 000 ₸", isPast = true),
    Ticket("t5", "🛍", "Craft Маркет «Зима»", "Mega Center, Атриум", "Сб, 21 дек 2025", "10:00 – 19:00", "Свободный вход", "Бесплатно", isPast = true),
)

// ─────────────────────────────────────────────────────────────────────────────
//  MyTicketsScreen — список билетов
// ─────────────────────────────────────────────────────────────────────────────

class MyTicketsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(0) }

        val upcoming = sampleTickets.filter { !it.isPast }
        val past = sampleTickets.filter { it.isPast }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Мои билеты", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Табы
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("Предстоящие ${upcoming.size}", "Прошедшие").forEachIndexed { i, label ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedTab = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(label, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }

            Spacer(Modifier.height(8.dp))

            val list = if (selectedTab == 0) upcoming else past
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎟", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Нет билетов", fontSize = 16.sp, color = NightTextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Найдите интересные события на карте", fontSize = 13.sp, color = NightTextTertiary, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list) { ticket ->
                        TicketCard(ticket, isPast = selectedTab == 1) {
                            nav.push(TicketDetailScreen(ticket.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket, isPast: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(NightSurface)
            .border(1.dp, if (!isPast) Violet600.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .clickableNoRipple(onClick),
    ) {
        Column {
            // Верхняя часть билета
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(
                            if (!isPast) Brush.linearGradient(BrandGradient)
                            else Brush.linearGradient(listOf(NightTextTertiary.copy(alpha = 0.30f), NightTextTertiary.copy(alpha = 0.30f)))
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text(ticket.emoji, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ticket.eventTitle, fontSize = 14.sp, color = if (!isPast) Color.White else NightTextSecondary, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    Spacer(Modifier.height(3.dp))
                    Text(ticket.venue, fontSize = 11.sp, color = NightTextSecondary)
                }
                if (!isPast) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Violet600.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("QR", fontSize = 10.sp, color = Violet400, fontWeight = FontWeight.ExtraBold) }
                }
            }

            // Разделитель — перфорация
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(NightBackground))
                Row(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(20) {
                        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
                Box(Modifier.size(16.dp).clip(CircleShape).background(NightBackground))
            }

            // Нижняя часть — детали
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TicketField("ДАТА", ticket.date)
                TicketField("ВРЕМЯ", ticket.time)
                TicketField("ЦЕНА", ticket.price)
            }
        }
    }
}

@Composable
private fun TicketField(label: String, value: String) {
    Column {
        Text(label, fontSize = 9.sp, color = NightTextTertiary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TicketDetailScreen — детальный билет с QR-кодом
// ─────────────────────────────────────────────────────────────────────────────

data class TicketDetailScreen(val ticketId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val ticket = sampleTickets.find { it.id == ticketId } ?: sampleTickets.first()

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Билет", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple {}.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("📤 Поделиться", fontSize = 11.sp, color = NightTextSecondary) }
            }

            Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                // Событие
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ticket.emoji, fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ticket.eventTitle, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.height(3.dp))
                        Text(ticket.venue, fontSize = 12.sp, color = NightTextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // QR блок
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // QR заглушка (псевдо-пиксели)
                        QrPlaceholder()
                        Spacer(Modifier.height(12.dp))
                        Text(ticket.id.uppercase(), fontSize = 11.sp, color = Color(0xFF56657A), letterSpacing = 2.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Детали
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TicketDetailRow("📅", "Дата", ticket.date)
                        TicketDetailRow("🕐", "Время", ticket.time)
                        TicketDetailRow("💺", "Место", ticket.seat)
                        TicketDetailRow("💰", "Стоимость", ticket.price)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🗺️", fontSize = 16.sp)
                        Text("Показать на карте", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QrPlaceholder() {
    // 8×8 псевдо-QR сетка из Box'ов
    val pattern = listOf(
        listOf(1,1,1,1,1,1,1,0),
        listOf(1,0,0,0,0,0,1,0),
        listOf(1,0,1,1,1,0,1,0),
        listOf(1,0,0,0,0,0,1,1),
        listOf(1,1,1,1,1,1,1,0),
        listOf(0,1,0,1,0,1,0,1),
        listOf(1,1,0,1,1,0,1,0),
        listOf(0,1,1,0,1,0,1,1),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        pattern.forEach { row ->
            Row {
                row.forEach { cell ->
                    Box(
                        Modifier.size(14.dp).padding(1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (cell == 1) Color(0xFF0A1420) else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketDetailRow(emoji: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BookingSuccessScreen — подтверждение покупки
// ─────────────────────────────────────────────────────────────────────────────

data class BookingSuccessScreen(
    val eventTitle: String,
    val eventEmoji: String,
    val date: String,
    val price: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Успех — анимированный чекмарк-иконка
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Soft.copy(alpha = 0.15f)).border(3.dp, Soft.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("✓", fontSize = 42.sp, color = Soft, fontWeight = FontWeight.ExtraBold) }

            Spacer(Modifier.height(24.dp))
            Text("Готово!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Билет куплен успешно", fontSize = 16.sp, color = NightTextSecondary, textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            // Карточка
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NightSurface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(eventEmoji, fontSize = 36.sp)
                Spacer(Modifier.height(10.dp))
                Text(eventTitle, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(date, fontSize = 13.sp, color = NightTextSecondary)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("ОПЛАЧЕНО", fontSize = 9.sp, color = NightTextTertiary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(price, fontSize = 16.sp, color = Soft, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Soft.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("✓ Подтверждён", fontSize = 12.sp, color = Soft, fontWeight = FontWeight.SemiBold) }
                }
            }

            Spacer(Modifier.weight(1f))

            // Кнопки
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.push(MyTicketsScreen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Смотреть билет", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple { nav.popUntilRoot() },
                contentAlignment = Alignment.Center,
            ) { Text("На главную", color = NightTextSecondary, fontSize = 14.sp) }
            Spacer(Modifier.height(40.dp))
        }
    }
}
