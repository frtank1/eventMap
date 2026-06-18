package com.eventmap.invite.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.eventmap.invite.ui.models.InviteGuest
import com.eventmap.invite.ui.models.PartyType
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  N2 · Создание приглашения (тип + шаблон)
//  N3 · Гости и рассылка
// =====================================================================

class CreateInviteScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var step by remember { mutableStateOf(0) }

        when (step) {
            0 -> InviteTypeView(onNext = { step = 1 }, onBack = { nav.pop() })
            1 -> GuestPickerView(onSend = { nav.pop() }, onBack = { step = 0 })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  N2: Тип + шаблон
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InviteTypeView(onNext: () -> Unit, onBack: () -> Unit) {
    var selectedType by remember { mutableStateOf(PartyType.Birthday) }
    var title by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = NightSurface,
        focusedContainerColor = NightSurface,
        unfocusedTextColor = Color.White,
        focusedTextColor = Color.White,
        unfocusedLabelColor = NightTextSecondary,
        focusedLabelColor = Violet400,
        unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
        focusedBorderColor = Violet600,
        cursorColor = Violet600,
    )

    Column(
        Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
    ) {
        // Топ-бар
        Row(
            Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                .clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text("Создать приглашение", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        // Тип праздника
        SectionLabel("ТИП ПРАЗДНИКА")
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PartyType.entries) { t ->
                val sel = t == selectedType
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                        .border(1.dp, if (sel) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickableNoRipple { selectedType = t }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(t.emoji, fontSize = 24.sp)
                    Text(t.label, fontSize = 11.sp, color = if (sel) Violet400 else NightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Карусель шаблонов (превью-заглушка)
        SectionLabel("ШАБЛОН ОТКРЫТКИ")
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(3) { i ->
                Box(
                    Modifier.size(width = 140.dp, height = 200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                when (i) {
                                    0 -> listOf(Color(0xFF2D1060), Color(0xFF1A0D3A))
                                    1 -> listOf(Color(0xFF0D2A1A), Color(0xFF0A1420))
                                    else -> listOf(Color(0xFF2A0D1A), Color(0xFF1A0820))
                                },
                            ),
                        )
                        .border(if (i == 0) 2.dp else 0.dp, Violet600.copy(alpha = 0.60f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(selectedType.emoji, fontSize = 36.sp)
                        Text("Шаблон ${i + 1}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Поля
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("ДЕТАЛИ")
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название события") },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("Место") },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))

        // Приватность
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("🔒 Приватное событие", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Виден только приглашённым, не на карте", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(
                checked = isPrivate,
                onCheckedChange = { isPrivate = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Violet600, uncheckedTrackColor = Color.White.copy(alpha = 0.12f)),
            )
        }

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                .clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onNext),
            contentAlignment = Alignment.Center,
        ) { Text("Выбрать гостей →", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  N3: Гости и рассылка
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GuestPickerView(onSend: () -> Unit, onBack: () -> Unit) {
    val friends = remember {
        listOf(
            InviteGuest("g1", "Тимур Ахметов", "timur_a"),
            InviteGuest("g2", "Зарина", "zarina"),
            InviteGuest("g3", "Данияр", "daniyar_kz"),
            InviteGuest("g4", "Арайлым", "arailym"),
            InviteGuest("g5", "Нурлан", "nurlan"),
        )
    }
    val selected = remember { mutableStateOf(setOf<String>()) }
    val groups = listOf("Семья 👨‍👩‍👧", "Универ 🎓", "Работа 💼")
    var selectedGroup by remember { mutableStateOf<String?>(null) }

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
                Text("Выбрать гостей", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                if (selected.value.isNotEmpty()) {
                    Text("Выбрано: ${selected.value.size}", fontSize = 12.sp, color = Violet400, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Группы
        SectionLabel("ГРУППЫ")
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups) { g ->
                val sel = g == selectedGroup
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                        .clickableNoRipple { selectedGroup = if (sel) null else g }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(g, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("ДРУЗЬЯ")

        friends.forEach { f ->
            val isSel = f.id in selected.value
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) Violet600.copy(alpha = 0.12f) else NightSurface)
                    .border(1.dp, if (isSel) Violet600.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickableNoRipple {
                        selected.value = if (isSel) selected.value - f.id else selected.value + f.id
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Violet600.copy(alpha = 0.18f), Violet600.copy(alpha = 0.18f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isSel) "✓" else f.name.take(1).uppercase(), fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("@${f.username}", fontSize = 11.sp, color = NightTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Ссылка для гостей без приложения
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .background(Color.Transparent).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔗", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Без приложения?", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Поделись ссылкой или QR-кодом", fontSize = 12.sp, color = NightTextSecondary)
                }
                Text("Скопировать", fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(20.dp))

        val n = selected.value.size
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (n > 0) Brush.linearGradient(BrandGradient)
                    else Brush.linearGradient(listOf(NightSurface, NightSurface)),
                )
                .clickableNoRipple { if (n > 0) onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (n > 0) "💌 Разослать $n приглашений" else "Выбери гостей",
                color = if (n > 0) Color.White else NightTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
}
