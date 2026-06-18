package com.eventmap.moderation.ui.screens

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
//  J1 · Жалоба    J2 · Мои жалобы    J3 · Ограничение аккаунта
// =====================================================================

private val reportReasons = listOf(
    "Спам" to "🚫",
    "Опасный контент" to "⚠️",
    "18+ без метки" to "🔞",
    "Фейк / недостоверно" to "🎭",
    "Оскорбления" to "😡",
    "Авторские права" to "©️",
    "Другое" to "💬",
)

// ─────────────────────────────────────────────────────────────────────────────
//  J1: форма жалобы
// ─────────────────────────────────────────────────────────────────────────────

data class ReportScreen(val contextLabel: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selected by remember { mutableStateOf<Int?>(null) }
        var sent by remember { mutableStateOf(false) }

        if (sent) {
            ReportSentView(onClose = { nav.pop() })
            return
        }

        Column(
            Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Пожаловаться", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(contextLabel, fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Text("ПРИЧИНА", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reportReasons.forEachIndexed { i, (label, emoji) ->
                    val sel = i == selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Violet600.copy(alpha = 0.15f) else NightSurface)
                            .border(1.dp, if (sel) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { selected = i }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (sel) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Violet600), contentAlignment = Alignment.Center) {
                                Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected != null) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (selected != null) sent = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("Отправить", color = if (selected != null) Color.White else NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ReportSentView(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✅", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("Жалоба отправлена", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Модераторы рассмотрят её в течение 24 часов", color = NightTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onClose),
            contentAlignment = Alignment.Center,
        ) { Text("Закрыть", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  J2: Мои жалобы
// ─────────────────────────────────────────────────────────────────────────────

class MyReportsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val reports = remember {
            listOf(
                Triple("Фейк событие", "на проверке", 0),
                Triple("Спам в комментариях", "принята", 1),
                Triple("Оскорбления", "отклонена", 2),
            )
        }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("Мои жалобы", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reports.forEach { (text, status, type) ->
                    val (statusLabel, statusColor, bg) = when (type) {
                        0    -> Triple("На проверке", Color(0xFFFFB300), Color(0xFFFFB300).copy(alpha = 0.08f))
                        1    -> Triple("Принята", Soft, Soft.copy(alpha = 0.08f))
                        else -> Triple("Отклонена", NightTextTertiary, NightSurface)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Жалоба #${1000 + type}", fontSize = 11.sp, color = NightTextTertiary, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) { Text(statusLabel, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  J3: Ограничение аккаунта
// ─────────────────────────────────────────────────────────────────────────────

class AccountRestrictedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Danger.copy(alpha = 0.12f))
                    .border(2.dp, Danger.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("⛔", fontSize = 36.sp) }

            Spacer(Modifier.height(20.dp))
            Text("Аккаунт ограничен", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("до 20 июня 2026 г.", fontSize = 16.sp, color = Danger, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Причина: нарушение правил сообщества (спам)", fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center)

            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("ДОСТУПНО", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                listOf("✓ Просматривать карту", "✓ Смотреть события", "✓ Читать комментарии").forEach {
                    Text(it, fontSize = 14.sp, color = Soft)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                Text("НЕДОСТУПНО", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                listOf("✕ Создавать события", "✕ Комментировать", "✕ Писать сообщения").forEach {
                    Text(it, fontSize = 14.sp, color = Danger.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("Оспорить решение", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

private val NightTextTertiary = Color(0xFF56657A)
