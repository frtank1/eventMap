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
)

/**
 * Style preset for the map. Each maps to a Mapbox Studio style URL
 * configured in MapStyles object.
 */
enum class MapStyle {
    DayLight,
    NightDark,
    Nauryz,    // праздничный с орнаментом
    NewYear,   // снежный
    Custom     // user-defined
}

/**
 * Platform-specific Mapbox MapView wrapper. Implemented in androidMain
 * and iosMain via expect/actual.
 *
 * Responsibilities:
 *   - Render base map (streets, parks, buildings) via Mapbox SDK
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
     * Project geo-point to screen offset (in dp, relative to the MapView).
     * Returns null if the point is outside the visible area.
     */
    fun project(point: LatLng): Offset?

    /**
     * Inverse projection — for tap handling.
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
    style: MapStyle = MapStyle.DayLight,
    modifier: Modifier = Modifier,
    onMapReady: (EventMapController) -> Unit = {},
)

/**
 * Стили карты. Пока используем стандартные стили Mapbox (работают из коробки
 * с любым публичным токеном). Позже заменить на свои из Mapbox Studio под
 * палитру Aurora Violet (см. MAPBOX_SETUP.md, шаг 6).
 */
object MapStyles {
    const val DAY_LIGHT = "mapbox://styles/mapbox/light-v11"
    const val NIGHT_DARK = "mapbox://styles/mapbox/dark-v11"
    const val NAURYZ = "mapbox://styles/mapbox/dark-v11"
    const val NEW_YEAR = "mapbox://styles/mapbox/dark-v11"

    fun urlFor(style: MapStyle): String = when (style) {
        MapStyle.DayLight -> DAY_LIGHT
        MapStyle.NightDark -> NIGHT_DARK
        MapStyle.Nauryz -> NAURYZ
        MapStyle.NewYear -> NEW_YEAR
        MapStyle.Custom -> DAY_LIGHT // fallback
    }
}
