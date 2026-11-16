package com.eventmap.map.city

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import com.eventmap.event.models.EventVenue
import com.eventmap.event.models.validBuildingId

internal val cityJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val cityJsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

@Serializable
enum class CityEntranceStatus {
    @SerialName("mapped") MAPPED,
    @SerialName("not_mapped") NOT_MAPPED,
    @SerialName("unreviewed") UNREVIEWED,
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
enum class CityEntranceKind {
    @SerialName("main") MAIN,
    @SerialName("yes") YES,
    @SerialName("staircase") STAIRCASE,
    @SerialName("home") HOME,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class CityWheelchair {
    @SerialName("yes") YES,
    @SerialName("no") NO,
    @SerialName("limited") LIMITED,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
data class CityEntrance(
    val id: String, val lat: Double, val lng: Double,
    val kind: CityEntranceKind, val wheelchair: CityWheelchair,
)

@Serializable
data class CityMessage(
    val v: Int = 0, val type: String = "", val id: String = "", val title: String = "",
    val lat: Double? = null, val lng: Double? = null, val state: String = "", val release: String = "",
    val ids: List<String> = emptyList(),
    val address: String = "", val addressStatus: String = "missing",
    val addressSource: String = "", val sourceRef: String = "", val geometryKind: String = "point",
    val entranceStatus: CityEntranceStatus = CityEntranceStatus.UNREVIEWED,
    val entrances: List<CityEntrance> = emptyList(),
    val buildingId: String? = null,
)

fun parseCityMessage(raw: String): CityMessage? {
    if (raw.length > 4096) return null
    val json = runCatching { cityJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    // Do not coerce string coordinates or partially specified entrance records.
    fun numeric(value: JsonElement?) = value is JsonPrimitive && !value.isString &&
        cityJsonNumber.matches(value.content) && value.doubleOrNull != null
    for (key in listOf("lat", "lng")) if (json[key] != null && json[key] != JsonNull && !numeric(json[key])) return null
    json["entrances"]?.let { value ->
        val entries = value as? JsonArray ?: return null
        if (entries.size > 8) return null
        for (entry in entries) {
            val e = entry as? JsonObject ?: return null
            if (!numeric(e["lat"]) || !numeric(e["lng"]) ||
                listOf("id", "kind", "wheelchair").any { (e[it] as? JsonPrimitive)?.isString != true }) return null
        }
    }
    val m = runCatching { cityJson.decodeFromJsonElement<CityMessage>(json) }.getOrNull() ?: return null
    if (m.v != 1 || m.type !in setOf("ready", "place", "event", "status", "content", "error", "covers")) return null
    if (m.ids.size > 8 || m.ids.any { it.isBlank() || it.length > 100 } || m.ids.distinct().size != m.ids.size) return null
    if (m.id.length > 100 || m.title.length > 120 || m.release.length > 64 || m.state.length > 64) return null
    if (m.address.length > 202 || m.addressSource.length > 40 || m.sourceRef.length > 80 ||
        listOf(m.address,m.addressSource,m.sourceRef).any { s -> s.any { it.code < 32 || it.code == 127 } } ||
        m.addressStatus !in setOf("missing", "source", "ambiguous", "unavailable") ||
        m.geometryKind !in setOf("point", "footprint", "stylized")) return null
    if (m.addressStatus == "source" && (m.address.isBlank() || m.addressSource.isBlank())) return null
    if (m.entrances.size > 8 || m.entrances.map { it.id }.distinct().size != m.entrances.size ||
        m.entrances.any { it.id.length > 100 || !it.id.matches(Regex("node/[1-9][0-9]*")) ||
            !it.lat.isFinite() || !it.lng.isFinite() || it.lat !in 50.9..51.4 || it.lng !in 71.1..71.9 }) return null
    if ((m.entranceStatus == CityEntranceStatus.MAPPED) != m.entrances.isNotEmpty()) return null
    if (m.type == "event" && m.id.isBlank()) return null
    if (m.buildingId != null && (!validBuildingId(m.buildingId) || m.geometryKind == "point")) return null
    if (m.type == "place" && (m.title.isBlank() || m.lat == null || m.lng == null ||
            !m.lat.isFinite() || !m.lng.isFinite() || m.lat !in 50.9..51.4 || m.lng !in 71.1..71.9)) return null
    return m
}

fun CityMessage.entranceStatusLabel(): String = when (entranceStatus) {
    CityEntranceStatus.MAPPED -> "Входы отмечены в OpenStreetMap (OSM). Это не официальные данные и не результат обследования на месте; общедоступность и доступность для коляски не гарантированы."
    CityEntranceStatus.NOT_MAPPED -> "Входы не отмечены в данных OSM. Это не означает, что входов нет."
    CityEntranceStatus.UNREVIEWED -> "Данные о входах не проверены. Подтверждённого входа здесь нет."
    CityEntranceStatus.UNAVAILABLE -> "Данные о входах сейчас недоступны. Подтверждённого входа здесь нет."
}

fun CityEntrance.label(): String {
    val kindLabel = when (kind) {
        CityEntranceKind.MAIN -> "Главный вход"
        CityEntranceKind.YES -> "Вход"
        CityEntranceKind.STAIRCASE -> "Вход к лестнице"
        CityEntranceKind.HOME -> "Вход в жильё"
        CityEntranceKind.UNKNOWN -> "Тип входа неизвестен"
    }
    val wheelchairLabel = when (wheelchair) {
        CityWheelchair.YES -> "да"
        CityWheelchair.NO -> "нет"
        CityWheelchair.LIMITED -> "ограниченно"
        CityWheelchair.UNKNOWN -> "неизвестно"
    }
    return "$kindLabel · OSM $id\n$lat, $lng\nКоляска по данным OSM: $wheelchairLabel"
}

data class CityCreationPoint(val lat: Double, val lng: Double, val locationLabel: String, val entranceId: String?, val venue: EventVenue? = null)

/** Unknown/stale selection never replaces the original point. No centroid/GPS inference. */
fun CityMessage.creationPoint(selectedEntranceId: String? = null): CityCreationPoint {
    require(type == "place" && lat != null && lng != null)
    val entrance = entrances.firstOrNull { entranceStatus == CityEntranceStatus.MAPPED && it.id == selectedEntranceId }
    val originalLabel = locationLabel()
    val note = entrance?.let { " · вход OSM ${it.id}" }.orEmpty()
    val label = if (originalLabel.length + note.length <= 255) originalLabel + note else originalLabel
    return CityCreationPoint(entrance?.lat ?: lat, entrance?.lng ?: lng, label, entrance?.id, buildingId?.let { EventVenue(it) })
}

fun CityCreationPoint.selectionLabel(): String = if (entranceId == null)
    "Вход не выбран: сохраняется исходная точка на карте, не подтверждённый вход. Уточни место встречи в описании."
else "Выбран вход OSM $entranceId: $lat, $lng. Для события будут использованы эти координаты."

internal fun CityMessage.locationLabel(): String = when {
    address.isBlank() -> title
    title == "Здание" || title == "Точка на карте" -> address
    else -> "$address · $title".take(255)
}

internal fun CityMessage.addressLabel(): String = when (addressStatus) {
    "source" -> "$address\nИсточник: $addressSource"
    "ambiguous" -> "У здания несколько адресов. Уточни вход в форме события."
    "unavailable" -> "Адресные данные сейчас недоступны. Координаты точки сохранены."
    else -> "Адрес не найден в данных карты. Уточни его в форме события."
}

@Serializable
internal data class CityEvent(
    val id: String, val lat: Double, val lng: Double, val title: String = "Событие",
    val category: String = "", val status: String = "soon", val goingCount: Int = 0,
    val endAt: String = "", val expiresAtMs: Long? = null,
    val kind: String = "planned", val sourceType: String = "initiator",
    val capacity: Int? = null, val confirmationCount: Int = 0,
    val startAt: String = "", val visibility: String = "public",
    val coverRef: String? = null,
)

@Serializable
internal data class CityCover(val id: String, val ref: String, val data: String)

@Serializable
internal data class CitySnapshot(
    val v: Int = 1, val type: String = "sync", val manifestUrl: String,
    val events: List<CityEvent>, val night: Boolean, val theme: String, val active: Boolean,
    val focusId: String = "baiterek", val focusRevision: Int = 0, val updateRevision: Int = 0,
    val interactionBlocked: Boolean = false,
    val covers: List<CityCover> = emptyList(),
)

/** Only serialized public scene state crosses this boundary. Never tokens or API credentials. */
@Composable
expect fun GameCityView(payload: String, modifier: Modifier = Modifier, onMessage: (String) -> Unit)
