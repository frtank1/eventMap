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

// =====================================================================
//  R3 · Капсула времени
//  Открывается лонг-тапом по месту на карте
// =====================================================================

class TimeCapsuleScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        // Тип получателя: 0 = другу, 1 = себе через год, 2 = всем
        var recipientType by remember { mutableStateOf(0) }

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
                    Text("📍 ул. Туран 37, Астана", fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
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

            Spacer(Modifier.height(24.dp))

            // Фото-заглушка
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NightSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📷", fontSize = 32.sp)
                    Text("Добавить фото", color = NightTextSecondary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Поле послания
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NightSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text("Напиши что-нибудь…", color = NightTextSecondary, fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))

            // Получатель
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПОЛУЧАТЕЛЬ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

                val recipients = listOf(
                    "👥" to "Другу" to "Откроется только на этом месте",
                    "🕐" to "Себе через год" to "Напоминание придёт через 365 дней",
                    "🌐" to "Всем, кто придёт" to "Виден любому посетителю этого места",
                )
                recipients.forEachIndexed { i, (iconLabel, desc) ->
                    val (icon, label) = iconLabel
                    val selected = i == recipientType
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Violet600.copy(alpha = 0.15f) else NightSurface)
                            .border(1.dp, if (selected) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { recipientType = i }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (selected) Violet600.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(icon, fontSize = 18.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(desc, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (selected) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Violet600), contentAlignment = Alignment.Center) {
                                Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { nav.pop() },
                contentAlignment = Alignment.Center,
            ) {
                Text("💊 Закопать капсулу", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
