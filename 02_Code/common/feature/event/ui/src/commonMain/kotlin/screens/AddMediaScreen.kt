package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  D5 — Добавить медиа к событию
//  Figma «🎬 D · Событие»  node 51:90
// =====================================================================

data class AddMediaScreen(val eventId: String) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var caption by remember { mutableStateOf("") }

        Column(
            Modifier.fillMaxSize().background(NightBackground),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Добавить медиа", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                // Upload area
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickableNoRipple { /* open picker */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Сними или выбери из галереи",
                            color = NightTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "фото · видео до 30 сек",
                            color = NightTextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Caption
                FieldLabel("ПОДПИСЬ")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (caption.isEmpty()) {
                        Text(
                            "Напиши что-нибудь о моменте…",
                            color = NightTextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                // Emoji shortcuts
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in listOf("😍", "🔥", "🎤", "🎵", "👏", "🕺")) {
                        Text(e, fontSize = 22.sp, modifier = Modifier.clickableNoRipple { caption += e })
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Event linked
                FieldLabel("СОБЫТИЕ")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Стендап-вечер · Open Mic",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Publish CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { nav.pop() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Опубликовать",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = NightTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
