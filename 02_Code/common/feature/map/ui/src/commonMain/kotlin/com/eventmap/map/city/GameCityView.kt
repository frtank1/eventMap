package com.eventmap.map.city

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val cityJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
internal data class CityMessage(
    val v: Int = 0, val type: String = "", val id: String = "", val title: String = "",
    val lat: Double? = null, val lng: Double? = null, val state: String = "", val release: String = "",
)

internal fun parseCityMessage(raw: String): CityMessage? {
    if (raw.length > 4096) return null
    val m = runCatching { cityJson.decodeFromString<CityMessage>(raw) }.getOrNull() ?: return null
    if (m.v != 1 || m.type !in setOf("ready", "place", "event", "status", "content", "error")) return null
    if (m.id.length > 100 || m.title.length > 120 || m.release.length > 64 || m.state.length > 64) return null
    if (m.type == "event" && m.id.isBlank()) return null
    if (m.type == "place" && (m.title.isBlank() || m.lat == null || m.lng == null ||
            !m.lat.isFinite() || !m.lng.isFinite() || m.lat !in 50.9..51.4 || m.lng !in 71.1..71.9)) return null
    return m
}

@Serializable
internal data class CityEvent(val id: String, val lat: Double, val lng: Double)

@Serializable
internal data class CitySnapshot(
    val v: Int = 1, val type: String = "sync", val manifestUrl: String,
    val events: List<CityEvent>, val night: Boolean, val theme: String, val active: Boolean,
    val focusId: String = "baiterek", val focusRevision: Int = 0, val updateRevision: Int = 0,
)

/** Only serialized public scene state crosses this boundary. Never tokens or API credentials. */
@Composable
expect fun GameCityView(payload: String, modifier: Modifier = Modifier, onMessage: (String) -> Unit)
