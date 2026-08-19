package com.eventmap.game.ui.screens

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
import androidx.compose.runtime.collectAsState
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
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.game.ui.viewmodel.CapsuleSubmit
import com.eventmap.game.ui.viewmodel.TimeCapsuleViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  R3 · Капсула времени — живая: POST /capsules (гео телефона),
//  «капсулы рядом» + открытие (бэк сверяет расстояние).
// =====================================================================

class TimeCapsuleScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<TimeCapsuleViewModel>()
        val fix by vm.fix.collectAsState()
        val nearby by vm.nearby.collectAsState()
        val submit by vm.submit.collectAsState()
        val opened by vm.opened.collectAsState()

        var message by remember { mutableStateOf("") }
        // 0 = другу (скоро), 1 = себе через год, 2 = всем
        var recipientType by remember { mutableStateOf(2) }

        // Успех — полноэкранный стейт
        if (submit is CapsuleSubmit.Done) {
            Column(
                Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("💊", fontSize = 56.sp)
                Spacer(Modifier.height(14.dp))
                Text("Капсула закопана!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (recipientType == 1) "Напомним о ней через год — на этом самом месте."
                    else "Её найдёт и откроет тот, кто окажется здесь.",
                    fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("Готово", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            }
            return
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            // Топ-бар
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
                    Text("Капсула времени", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        fix?.let { f ->
                            fun r(v: Double) = (v * 10000).toInt() / 10000.0
                            "📍 ${r(f.lat)}, ${r(f.lng)} · твоя точка"
                        } ?: "📍 Определяем твоё место…",
                        fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // Иллюстрация
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(100.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Violet600.copy(alpha = 0.25f), Color(0xFF1A0D3A)))).border(2.dp, Violet600.copy(alpha = 0.50f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("💊", fontSize = 44.sp) }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Оставь послание в этом месте",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Его откроют только те, кто придёт сюда",
                fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 4.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Поле послания (живое)
            OutlinedTextField(
                value = message,
                onValueChange = { message = it; vm.resetError() },
                placeholder = { Text("Напиши что-нибудь…", color = NightTextSecondary) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
                    unfocusedTextColor = Color.White, focusedTextColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f), focusedBorderColor = Violet600, cursorColor = Violet600,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Получатель
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПОЛУЧАТЕЛЬ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

                data class Rec(val icon: String, val label: String, val desc: String, val enabled: Boolean)
                val recipients = listOf(
                    Rec("👥", "Другу", "Скоро: выбор друга-получателя", enabled = false),
                    Rec("🕐", "Себе через год", "Напоминание придёт через 365 дней", enabled = true),
                    Rec("🌐", "Всем, кто придёт", "Виден любому посетителю этого места", enabled = true),
                )
                recipients.forEachIndexed { i, r ->
                    val selected = i == recipientType
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Violet600.copy(alpha = 0.15f) else NightSurface)
                            .border(1.dp, if (selected) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { if (r.enabled) recipientType = i }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (selected) Violet600.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(r.icon, fontSize = 18.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 14.sp, color = if (r.enabled) Color.White else NightTextSecondary, fontWeight = FontWeight.SemiBold)
                            Text(r.desc, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (selected) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Violet600), contentAlignment = Alignment.Center) {
                                Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            (submit as? CapsuleSubmit.Error)?.let { err ->
                Text(err.message, fontSize = 12.sp, color = Color(0xFFFF7A8A), lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            Spacer(Modifier.height(16.dp))

            // CTA
            val sending = submit is CapsuleSubmit.Sending
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (sending) Brush.linearGradient(listOf(NightSurface, NightSurface)) else Brush.linearGradient(BrandGradient))
                    .clickableNoRipple {
                        if (!sending) vm.create(message, if (recipientType == 1) "self" else "public")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (sending) "Закапываем…" else "💊 Закопать капсулу",
                    color = if (sending) NightTextSecondary else Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                )
            }

            // Капсулы рядом
            if (nearby.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("КАПСУЛЫ РЯДОМ · ${nearby.size}", color = NightTextSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nearby.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (c.opened) "📭" else "💊", fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        c.mine -> "Твоя капсула"
                                        c.opened -> "Открытая капсула"
                                        else -> "Чья-то капсула"
                                    },
                                    fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                                )
                                if (c.opened && !c.bodyText.isNullOrBlank()) {
                                    Text("«${c.bodyText}»", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            if (!c.opened) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(10.dp)).background(Violet600.copy(alpha = 0.20f))
                                        .clickableNoRipple { vm.open(c) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                ) { Text("Открыть", fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        // Открытая капсула — карточка поверх
        opened?.let { c ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f))
                    .clickableNoRipple { vm.dismissOpened() },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(horizontal = 32.dp).clip(RoundedCornerShape(20.dp)).background(NightSurface).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("💌", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Послание из прошлого", fontSize = 15.sp, color = Soft, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        c.bodyText?.takeIf { it.isNotBlank() } ?: "Капсула пуста — но кто-то был здесь до тебя.",
                        fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Center, lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Нажми, чтобы закрыть", fontSize = 11.sp, color = NightTextSecondary)
                }
            }
        }
    }
}
