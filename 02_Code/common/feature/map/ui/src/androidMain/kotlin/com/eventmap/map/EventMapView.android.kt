package com.eventmap.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-реализация карты на mapbox-maps-android 11.x.
 * Токен скачивания SDK — в ~/.gradle/gradle.properties (MAPBOX_DOWNLOADS_TOKEN),
 * публичный токen — в strings.xml (mapbox_access_token) + meta-data манифеста.
 */
actual class EventMapController(
    private val mapboxMap: MapboxMap,
) {
    private val _viewport = MutableStateFlow(ViewportState(LatLng(51.1282, 71.4307), 13.0))
    actual val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    init {
        mapboxMap.subscribeCameraChanged {
            val s = mapboxMap.cameraState
            _viewport.value = ViewportState(
                center = LatLng(s.center.latitude(), s.center.longitude()),
                zoom = s.zoom,
                bearing = s.bearing,
                pitch = s.pitch,
            )
        }
    }

    actual fun project(point: LatLng): Offset? {
        val p = mapboxMap.pixelForCoordinate(Point.fromLngLat(point.lon, point.lat))
        return Offset(p.x.toFloat(), p.y.toFloat())
    }

    actual fun unproject(offset: Offset): LatLng {
        val c = mapboxMap.coordinateForPixel(ScreenCoordinate(offset.x.toDouble(), offset.y.toDouble()))
        return LatLng(c.latitude(), c.longitude())
    }

    actual fun setStyle(style: MapStyle) {
        mapboxMap.loadStyle(MapStyles.urlFor(style))
    }

    actual fun animateCamera(target: LatLng, zoom: Double, durationMs: Int) {
        mapboxMap.flyTo(
            cameraOptions {
                center(Point.fromLngLat(target.lon, target.lat))
                zoom(zoom)
            },
            MapAnimationOptions.mapAnimationOptions { duration(durationMs.toLong()) },
        )
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
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                scalebar.enabled = false // линейка-шкала сверху — визуальный мусор
                mapboxMap.apply {
                    setCamera(
                        cameraOptions {
                            center(Point.fromLngLat(initialCenter.lon, initialCenter.lat))
                            zoom(initialZoom)
                        }
                    )
                    loadStyle(MapStyles.urlFor(style)) { onMapReady(EventMapController(this)) }
                }
            }
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) { onDispose { mapView.onDestroy() } }
}
