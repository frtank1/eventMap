package com.eventmap.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS — ВРЕМЕННЫЙ placeholder без Mapbox SDK, чтобы проект собирался без токена.
 *
 * Реальная реализация на MapboxMaps iOS (через cocoapods + UIKitView) подключается
 * ПОСЛЕ настройки SDK — см. MAPBOX_SETUP.md:
 *   1) kotlin { cocoapods { pod("MapboxMaps", version = "~> 11.x") } }
 *   2) MBXAccessToken в iosApp/Info.plist, ~/.netrc с secret-токеном
 * После этого заменить этот файл боевой реализацией EventMapController/EventMap.
 */
actual class EventMapController {
    private val _viewport = MutableStateFlow(
        ViewportState(LatLng(51.1282, 71.4307), 13.0)
    )
    actual val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    actual fun project(point: LatLng): Offset? = null
    actual fun unproject(offset: Offset): LatLng = _viewport.value.center
    actual fun setStyle(style: MapStyle) { /* no-op до подключения Mapbox */ }
    actual fun animateCamera(target: LatLng, zoom: Double, durationMs: Int) {
        _viewport.value = _viewport.value.copy(center = target, zoom = zoom)
    }
}

@Composable
actual fun EventMap(
    initialCenter: LatLng,
    initialZoom: Double,
    style: MapStyle,
    modifier: Modifier,
    onMapReady: (EventMapController) -> Unit,
) {
    val controller = remember { EventMapController() }
    LaunchedEffect(controller) { onMapReady(controller) }

    Box(modifier.fillMaxSize().background(Color(0xFF0C1825)), Alignment.Center) {
        Text("Map preview (Mapbox не подключён)", color = Color.White)
    }
}
