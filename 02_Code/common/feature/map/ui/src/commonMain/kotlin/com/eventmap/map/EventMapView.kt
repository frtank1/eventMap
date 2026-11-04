package com.eventmap.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.StateFlow

/**
 * Geo-coordinates point (WGS-84).
 */
data class LatLng(val lat: Double, val lon: Double)

/**
 * Current map viewport state. Updated on every pan/zoom/rotate.
 * Used by the Compose Canvas overlay to re-project event objects.
 */
data class ViewportState(
    val center: LatLng,
    val zoom: Double,
    val bearing: Double = 0.0,
    val pitch: Double = 0.0,
    // Invalidate overlays when projection changes without moving the camera (e.g. resize).
    val projectionRevision: Long = 0L,
)

/**
 * Provider-independent theme. Holiday/custom presets currently use the dark theme.
 */
enum class MapStyle {
    DayLight,
    NightDark,
    Nauryz,    // праздничный с орнаментом
    NewYear,   // снежный
    Custom     // user-defined
}

/**
 * Platform map wrapper. Android selects 2GIS or Mapbox at build time.
 * Other targets currently provide placeholders.
 *
 * Responsibilities:
 *   - Render the base map (streets, parks, buildings)
 *   - Expose viewport state to be observed by the Compose overlay
 *   - Provide projection LatLng -> screen Offset for overlay rendering
 *   - Apply theme styles (day/night/holiday) on the fly
 */
expect class EventMapController {
    /**
     * StateFlow of the current viewport. Compose overlay subscribes
     * to this and re-renders animated event objects on each emit.
     */
    val viewport: StateFlow<ViewportState>

    /**
     * Project geo-point to screen offset (physical pixels, relative to the MapView).
     * Returns null if the point is outside the visible area.
     */
    fun project(point: LatLng): Offset?

    /**
     * Inverse projection of physical pixels — for tap handling.
     */
    fun unproject(offset: Offset): LatLng

    /**
     * Switch map style (theme).
     */
    fun setStyle(style: MapStyle)

    /**
     * Animate camera to a new position.
     */
    fun animateCamera(
        target: LatLng,
        zoom: Double = 14.0,
        durationMs: Int = 600,
    )
}

/**
 * Compose entry point. Returns a controller bound to the platform view.
 * The actual MapView is hosted via AndroidView (Android) or UIKitView (iOS).
 *
 * Use the controller in your screen ViewModel to query projection
 * and switch styles.
 */
@Composable
expect fun EventMap(
    initialCenter: LatLng,
    initialZoom: Double = 13.0,
    style: MapStyle = MapStyle.NightDark, // дефолт — тёмная под тему приложения
    modifier: Modifier = Modifier,
    onMapReady: (EventMapController) -> Unit = {},
)

/**
 * Стили карты. dark-v11/light-v11 — проверенные классические стили Mapbox
 * (Standard с lightPreset на части устройств не грузился — чёрный экран, 09.07).
 * Дефолт приложения — ТЁМНАЯ карта под палитру Aurora Violet; ☀️ — ручной день.
 */
object MapStyles {
    const val DAY_LIGHT = "mapbox://styles/mapbox/light-v11"
    const val NIGHT_DARK = "mapbox://styles/mapbox/dark-v11"

    fun urlFor(style: MapStyle): String = when (style) {
        MapStyle.DayLight -> DAY_LIGHT
        MapStyle.NightDark, MapStyle.Nauryz, MapStyle.NewYear, MapStyle.Custom -> NIGHT_DARK
    }
}
