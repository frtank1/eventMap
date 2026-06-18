package com.eventmap.game.ui.screens

import androidx.compose.foundation.background
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
//  R2 · «Твой год в городе» (Wrapped)
// =====================================================================

class WrappedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A0D3A), Color(0xFF0A1420)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌃", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Твой 2026",
                        fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ты прожил город на",
                        fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "47",
                            fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                            color = Color.Transparent,
                            modifier = Modifier.background(Brush.linearGradient(BrandGradient)).padding(horizontal = 4.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("событий", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Навигация
                Box(
                    Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 16.dp)
                        .size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.30f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(24.dp))

            // Стата-сетка
            val stats = listOf(
                "📍" to "Топ-место" to "Parq Concert Hall",
                "🎵" to "Топ-жанр" to "Концерты · 18 раз",
                "🚶" to "Пешком" to "214 км",
                "👥" to "Новых друзей" to "12 человек",
                "🗺" to "% города" to "34% исследовано",
                "🌙" to "Ночных событий" to "9 раз",
            )

            val rows = stats.chunked(2)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (iconLabel, value) ->
                            val (icon, label) = iconLabel
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NightSurface)
                                    .padding(16.dp),
                            ) {
                                Text(icon, fontSize = 24.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(value, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Мини-карта следов
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0D1A2A), Color(0xFF112030)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🗺", fontSize = 36.sp)
                    Text("Карта твоих следов", color = NightTextSecondary, fontSize = 13.sp)
                    Text("47 мест · 34% города", color = Violet400, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(24.dp))

            // CTA — поделиться
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) {
                Text("↗ Поделиться в сторис", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
