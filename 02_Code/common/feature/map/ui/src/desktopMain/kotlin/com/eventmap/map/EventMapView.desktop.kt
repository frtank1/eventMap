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
 * Desktop (JVM) implementation — placeholder.
 *
 * Mapbox не имеет нативного desktop-SDK. Варианты:
 *   - MapLibre Native (GL) через JNI,
 *   - статичный тайл-рендер,
 *   - превью-заглушка (текущая реализация).
 *
 * Сейчас отдаёт фиксированный viewport и не проецирует точки —
 * достаточно, чтобы проект собирался под desktop.
 */
actual class EventMapController {
    private val _viewport = MutableStateFlow(
        ViewportState(LatLng(51.1282, 71.4307), 13.0)
    )
    actual val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    actual fun project(point: LatLng): Offset? = null
    actual fun unproject(offset: Offset): LatLng = _viewport.value.center
    actual fun setStyle(style: MapStyle) { /* no-op */ }
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

    Box(modifier.fillMaxSize().background(Color(0xFF1B1B1F)), Alignment.Center) {
        Text("Map preview (desktop)", color = Color.White)
    }
}
