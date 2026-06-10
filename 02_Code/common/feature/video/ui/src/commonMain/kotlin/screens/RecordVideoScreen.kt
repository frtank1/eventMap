package com.eventmap.video.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

// E3 — экран записи. Камера/таймер подключаются платформенно (CameraX / AVFoundation).
class RecordVideoScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("📷 Камера (превью)", color = Color.White.copy(alpha = 0.5f))
            }
            Text("✕", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 44.dp, start = 16.dp).clickable { nav.pop() })

            Text("0:30", color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp))

            // кнопка записи
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFFF4757))) }
                Text("Удерживай для записи (30 сек)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
