package com.eventmap.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.dgis.sdk.DGis
import ru.dgis.sdk.Duration
import ru.dgis.sdk.PersonalDataCollectionConsent
import ru.dgis.sdk.ScreenPoint
import ru.dgis.sdk.coordinates.GeoPoint
import ru.dgis.sdk.coordinates.Latitude
import ru.dgis.sdk.coordinates.Longitude
import ru.dgis.sdk.map.CameraPosition
import ru.dgis.sdk.map.DefaultMapControllerViewModel
import ru.dgis.sdk.map.Fixed
import ru.dgis.sdk.map.MapAppearance
import ru.dgis.sdk.map.MapControllerOptions
import ru.dgis.sdk.map.MapTheme
import ru.dgis.sdk.map.MapView
import ru.dgis.sdk.map.Zoom
import ru.dgis.sdk.map.awaitMap
import ru.dgis.sdk.map.Map as DgisMap

/** A single SDK context per process, shared by the map and event location picker. */
private object DgisRuntime {
    private var sdkContext: ru.dgis.sdk.Context? = null

    @Synchronized
    fun get(context: Context): ru.dgis.sdk.Context = sdkContext ?: run {
        // Fail predictably before invoking native code when the key isn't provisioned.
        context.assets.open("dgissdk.key").use { require(it.read() != -1) }
        DGis.initialize(
            context.applicationContext,
            dataCollectConsent = PersonalDataCollectionConsent.DENIED,
        ).also { sdkContext = it }
    }
}

private fun LatLng.toGeoPoint() = GeoPoint(Latitude(lat), Longitude(lon))
private fun GeoPoint.toLatLng() = LatLng(latitude.value, longitude.value)
private fun MapStyle.appearance() = MapAppearance(
    Fixed(if (this == MapStyle.DayLight) MapTheme.defaultTheme else MapTheme.defaultDarkTheme)
)

actual class EventMapController internal constructor(private val map: DgisMap) {
    private var closed = false
    private var revision = 0L
    private fun snapshot(): ViewportState = map.camera.position.let {
        ViewportState(
            center = it.point.toLatLng(),
            zoom = it.zoom.value.toDouble(),
            bearing = it.bearing.value,
            pitch = it.tilt.value.toDouble(),
            projectionRevision = revision,
        )
    }

    private val state = MutableStateFlow(snapshot())
    actual val viewport: StateFlow<ViewportState> = state.asStateFlow()
    // SDK 14 exposes camera.changed, including resize/projection changes.
    private val cameraConnection = map.camera.changed.connect {
        if (!closed) {
            revision++
            state.value = snapshot()
        }
    }

    actual fun project(point: LatLng): Offset? {
        if (closed) return null
        return map.camera.projection.mapToScreen(point.toGeoPoint())?.let { Offset(it.x, it.y) }
    }

    actual fun unproject(offset: Offset): LatLng {
        if (closed) return state.value.center
        return map.camera.projection.screenToMap(ScreenPoint(offset.x, offset.y))?.toLatLng()
            ?: state.value.center
    }

    actual fun setStyle(style: MapStyle) {
        if (!closed) map.appearance = style.appearance()
    }

    actual fun animateCamera(target: LatLng, zoom: Double, durationMs: Int) {
        if (closed) return
        val position = map.camera.position.copy(point = target.toGeoPoint(), zoom = Zoom(zoom.toFloat()))
        if (durationMs <= 0) map.camera.position = position
        else map.camera.move(position, Duration.ofMilliseconds(durationMs.toLong()))
    }

    internal fun close() {
        closed = true
        cameraConnection.close()
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
    val currentOnReady by rememberUpdatedState(onMapReady)
    val currentStyle by rememberUpdatedState(style)
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var mapViewModel by remember { mutableStateOf<DefaultMapControllerViewModel?>(null) }
    var controller by remember { mutableStateOf<EventMapController?>(null) }

    LaunchedEffect(context, attempt) {
        failed = false
        var ownedViewModel: DefaultMapControllerViewModel? = null
        var ownedController: EventMapController? = null
        try {
            val sdk = DgisRuntime.get(context)
            val viewModel = DefaultMapControllerViewModel(
                sdk,
                MapControllerOptions(
                    position = CameraPosition(initialCenter.toGeoPoint(), Zoom(initialZoom.toFloat())),
                    mapAppearance = currentStyle.appearance(),
                ),
            )
            ownedViewModel = viewModel
            mapViewModel = viewModel
            val map = withTimeoutOrNull(20_000) { viewModel.awaitMap() }
                ?: error("2GIS map initialization timed out")
            val readyController = EventMapController(map)
            ownedController = readyController
            readyController.setStyle(currentStyle)
            controller = readyController
            currentOnReady(readyController)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            // Close subscriptions before destroying the native map.
            ownedController?.close()
            ownedViewModel?.close()
            controller = null
            mapViewModel = null
        }
    }

    LaunchedEffect(controller, style) { controller?.setStyle(style) }

    Box(modifier.background(if (style == MapStyle.DayLight) Color(0xFFF3F4F5) else Color(0xFF111821))) {
        mapViewModel?.let { viewModel ->
            key(viewModel) {
                AndroidView(
                    factory = { MapView(it).apply { setMapControllerViewModel(viewModel) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Карта временно недоступна", color = if (style == MapStyle.DayLight) Color.Black else Color.White)
                TextButton(onClick = { attempt++ }) { Text("Повторить") }
            }
        } else if (controller == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
