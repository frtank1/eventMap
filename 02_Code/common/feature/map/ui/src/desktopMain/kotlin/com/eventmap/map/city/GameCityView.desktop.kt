package com.eventmap.map.city
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
@Composable
actual fun GameCityView(payload:String,modifier:Modifier,onMessage:(String)->Unit) {
    LaunchedEffect(Unit) { onMessage("{\"v\":1,\"type\":\"error\",\"state\":\"mobile-only\"}") }
}
