package com.eventmap.invite.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.eventmap.invite.ui.models.RsvpStatus
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Danger
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  N4 · Пригласительное (у гостя)
//  N5 · RSVP-дашборд (организатор)
// =====================================================================

data class InviteCardScreen(
    val inviterName: String,
    val guestName: String,
    val partyEmoji: String,
    val eventTitle: String,
    val dateLabel: String,
    val place: String,
    val guestsGoing: Int,
    val guestsTotal: Int,
    val isOrganizer: Boolean = false,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        if (isOrganizer) RsvpDashboard(this, onBack = { nav.pop() })
        else GuestCardView(this, onBack = { nav.pop() })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  N4: Открытка гостя
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GuestCardView(s: InviteCardScreen, onBack: () -> Unit) {
    var rsvp by remember { mutableStateOf<RsvpStatus?>(null) }

    Column(
        Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
    ) {
        // Анимированная открытка
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0D3A), Color(0xFF0A1420)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.partyEmoji, fontSize = 60.sp, textAlign = TextAlign.Center)
                Text(s.inviterName, fontSize = 16.sp, color = Violet400, fontWeight = FontWeight.Bold)
                Text("приглашает тебя…", fontSize = 13.sp, color = NightTextSecondary)
                Text(s.guestName, fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 16.dp).size(36.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.30f)).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            // Заголовок
            Text(s.eventTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

            Spacer(Modifier.height(16.dp))

            // Детали
            InfoRow("🗓", s.dateLabel)
            Spacer(Modifier.height(8.dp))
            InfoRow("📍", s.place)
            Spacer(Modifier.height(8.dp))
            InfoRow("👥", "${s.guestsGoing} гостей уже придут · всего ${s.guestsTotal}")

            Spacer(Modifier.height(24.dp))

            // RSVP
            if (rsvp == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple { rsvp = RsvpStatus.Going },
                        contentAlignment = Alignment.Center,
                    ) { Text("✓ Приду", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(Danger.copy(alpha = 0.12f))
                            .border(1.dp, Danger.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                            .clickableNoRipple { rsvp = RsvpStatus.Declined },
                        contentAlignment = Alignment.Center,
                    ) { Text("Не смогу 😔", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (rsvp == RsvpStatus.Going) Soft.copy(alpha = 0.10f) else Danger.copy(alpha = 0.08f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (rsvp == RsvpStatus.Going) "✓ Ты в списке гостей!" else "Ответ отправлен 😔",
                        color = if (rsvp == RsvpStatus.Going) Soft else Danger,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // В календарь
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("📅 Добавить в календарь", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp) }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(emoji: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NightSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = Color.White)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  N5: RSVP-дашборд организатора
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RsvpDashboard(s: InviteCardScreen, onBack: () -> Unit) {
    val stats = listOf(
        Triple("Позвано", s.guestsTotal, NightTextSecondary),
        Triple("Придут", s.guestsGoing, Soft),
        Triple("Не смогут", 2, Danger),
        Triple("Молчат", s.guestsTotal - s.guestsGoing - 2, NightTextSecondary),
    )
    val guests = listOf(
        "Тимур А." to RsvpStatus.Going,
        "Зарина" to RsvpStatus.Going,
        "Данияр" to RsvpStatus.Declined,
        "Арайлым" to RsvpStatus.Pending,
        "Нурлан" to RsvpStatus.Pending,
        "+1 по ссылке" to RsvpStatus.Going,
    )

    Column(
        Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
    ) {
        // Топ-бар
        Row(
            Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                .clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RSVP", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(s.eventTitle, fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { }.padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text("✏️ Ред.", color = NightTextSecondary, fontSize = 12.sp) }
        }

        // Статистика
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            stats.forEach { (label, count, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", fontSize = 22.sp, color = color, fontWeight = FontWeight.ExtraBold)
                    Text(label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Кнопка напомнить
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(46.dp)
                .clip(RoundedCornerShape(12.dp)).background(Violet600.copy(alpha = 0.18f))
                .border(1.dp, Violet600.copy(alpha = 0.40f), RoundedCornerShape(12.dp)).clickableNoRipple { },
            contentAlignment = Alignment.Center,
        ) { Text("🔔 Напомнить молчунам (${s.guestsTotal - s.guestsGoing - 2})", color = Violet400, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(16.dp))

        // Список гостей
        Text("ГОСТИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))

        guests.forEach { (name, status) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(NightSurface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), fontSize = 14.sp, color = Violet400, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(name, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                val (label, color) = when (status) {
                    RsvpStatus.Going    -> "✓ Придёт"  to Soft
                    RsvpStatus.Declined -> "✕ Не придёт" to Danger
                    RsvpStatus.Pending  -> "Молчит"     to NightTextSecondary
                }
                Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
