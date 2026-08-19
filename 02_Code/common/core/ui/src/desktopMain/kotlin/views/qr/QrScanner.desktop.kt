package com.eventmap.views.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Сканера нет — экран чек-ина оставляет ручной ввод кода
actual val qrScannerAvailable: Boolean = false

@Composable
actual fun QrScannerView(onResult: (String) -> Unit, modifier: Modifier) {
    Box(modifier.background(Color(0xFF050510)), contentAlignment = Alignment.Center) {
        Text("Сканер — в мобильном приложении", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
    }
}
