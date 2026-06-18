package com.eventmap.auth.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  B1 · Авторизация (телефон / email)
//  B2 · OTP-код
//  B3 · Настройка профиля
// =====================================================================

// ─────────────────────────────────────────────────────────────────────────────
//  B1: AuthScreen
// ─────────────────────────────────────────────────────────────────────────────

class AuthScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var tab by remember { mutableStateOf(0) } // 0=phone, 1=email
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        Column(
            Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            // Лого
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text("📍", fontSize = 32.sp) }
            Spacer(Modifier.height(20.dp))
            Text("Войти в EventMap", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Алматы · Астана · и все города Казахстана", fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(36.dp))

            // Переключатель телефон / email
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(NightSurface).padding(4.dp),
            ) {
                Row {
                    listOf("Телефон", "Email").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (i == tab) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickableNoRipple { tab = i },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (i == tab) Color.White else NightTextSecondary, fontSize = 14.sp, fontWeight = if (i == tab) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (tab == 0) {
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("+7 (___) ___-__-__") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            } else {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(16.dp))

            val canProceed = if (tab == 0) phone.length >= 10 else email.contains("@")
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (canProceed) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (canProceed) nav.push(OtpScreen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Получить код", color = if (canProceed) Color.White else NightTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }

            Spacer(Modifier.height(24.dp))

            // Разделитель
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Text("  или  ", fontSize = 12.sp, color = NightTextSecondary)
                Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            }

            Spacer(Modifier.height(16.dp))

            // Соцсети
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("🍎" to "Apple", "🌐" to "Google").forEach { (emoji, label) ->
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                            .background(NightSurface).border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .clickableNoRipple {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(emoji, fontSize = 18.sp)
                            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Продолжая, ты принимаешь Условия использования\nи Политику конфиденциальности",
                fontSize = 11.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  B2: OtpScreen
// ─────────────────────────────────────────────────────────────────────────────

class OtpScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val cells = remember { mutableStateOf(List(6) { "" }) }
        val filled = cells.value.all { it.isNotEmpty() }

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))
            Text("Введи код", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Отправили SMS на +7 (705) •••-••-42", fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))

            // 6 ячеек OTP
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.value.forEachIndexed { i, digit ->
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                            .background(NightSurface)
                            .border(1.dp, if (digit.isNotEmpty()) Violet600.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .clickableNoRipple {
                                // Demo: fill all cells
                                cells.value = cells.value.toMutableList().also { list ->
                                    for (j in 0..i) list[j] = "${j + 1}"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit.ifEmpty { "·" }, fontSize = if (digit.isNotEmpty()) 20.sp else 24.sp,
                            color = if (digit.isNotEmpty()) Color.White else NightTextSecondary.copy(alpha = 0.4f),
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (filled) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (filled) nav.push(SetupProfileScreen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Подтвердить", color = if (filled) Color.White else NightTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }

            Spacer(Modifier.height(20.dp))
            Text("Не пришёл код?", fontSize = 13.sp, color = NightTextSecondary)
            Spacer(Modifier.height(6.dp))
            Text("Отправить повторно через 0:42", fontSize = 13.sp, color = Violet400, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  B3: SetupProfileScreen
// ─────────────────────────────────────────────────────────────────────────────

private val interestTags = listOf(
    "🎵 Музыка", "🎨 Арт", "🏋️ Спорт", "🎭 Театр", "☕ Кофейни",
    "🍕 Еда", "🎮 Игры", "🌿 Природа", "🎤 Стендап", "🎬 Кино",
    "🛍 Шопинг", "📚 Лекции",
)

class SetupProfileScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var username by remember { mutableStateOf("") }
        val selectedInterests = remember { mutableStateOf(setOf<Int>()) }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(64.dp))

            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Почти готово! 🎉", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Расскажи немного о себе", fontSize = 14.sp, color = NightTextSecondary)
                Spacer(Modifier.height(28.dp))

                // Аватар
                Box(
                    Modifier.size(96.dp).clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient))
                        .border(3.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 28.sp)
                        Text("Фото", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Имя пользователя") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            // Интересы
            Text("ТВОИ ИНТЕРЕСЫ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))

            val rows = interestTags.chunked(3)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIdx, tag ->
                            val i = rowIdx * 3 + colIdx
                            val sel = i in selectedInterests.value
                            Box(
                                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                    .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickableNoRipple { selectedInterests.value = if (sel) selectedInterests.value - i else selectedInterests.value + i },
                                contentAlignment = Alignment.Center,
                            ) { Text(tag, fontSize = 12.sp, color = Color.White, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                        // fill remaining cells in last row
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val canFinish = username.length >= 3 && selectedInterests.value.size >= 3
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (canFinish) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selectedInterests.value.size < 3) "Выбери хотя бы 3 интереса" else "Поехали 🚀",
                    color = if (canFinish) Color.White else NightTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
