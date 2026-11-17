package com.eventmap.map.city

import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Curated public events — the same list the web version publishes (02_Code/web/events.json,
 * copied into composeResources by game-city/build.mjs). Shown on the 3D map when the events
 * API is unreachable or empty, so the map is never blank. Read-only; nothing is uploaded.
 */
@Serializable
internal data class CuratedEvent(
    val id: String, val title: String, val lat: Double, val lng: Double, val startAt: String,
    val endAt: String? = null, val category: String = "", val place: String = "", val address: String = "",
    val description: String = "", val price: String = "", val url: String = "", val source: String = "",
)

internal fun parseCuratedEvents(text: String): List<CuratedEvent> =
    runCatching { cityJson.decodeFromString(ListSerializer(CuratedEvent.serializer()), text) }.getOrDefault(emptyList())
        .filter { it.id.isNotBlank() && it.lat in 50.9..51.4 && it.lng in 71.1..71.9 && isoToEpochMs(it.startAt) != null }

@OptIn(ExperimentalTime::class)
internal fun nowEpochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private const val HOUR_MS = 3_600_000L
private const val ASTANA_OFFSET_MS = 5 * HOUR_MS
private val isoTime = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:\d{2})$""")
private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

/** Epoch ms of an ISO-8601 time with an offset ("2026-12-05T19:00:00+05:00"), or null. */
internal fun isoToEpochMs(value: String): Long? {
    val g = isoTime.matchEntire(value)?.groupValues ?: return null
    val month = g[2].toInt(); val day = g[3].toInt(); val hour = g[4].toInt(); val minute = g[5].toInt()
    val second = g[6].ifEmpty { "0" }.toInt()
    if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null
    val offset = if (g[7] == "Z") 0L else {
        val sign = if (g[7][0] == '-') -1 else 1
        sign * (g[7].substring(1, 3).toLong() * 60 + g[7].substring(4, 6).toLong()) * 60_000L
    }
    return daysFromCivil(g[1].toInt(), month, day) * 86_400_000L + (hour * 3600L + minute * 60L + second) * 1000L - offset
}

/** Night by Astana clock (UTC+5), the same rule as the web version. */
internal fun astanaNight(nowMs: Long): Boolean {
    val hour = (nowMs + ASTANA_OFFSET_MS).floorDiv(HOUR_MS).mod(24)
    return hour < 8 || hour >= 18
}

/** Ended events are dropped; without an end time an event counts as three hours long. */
internal fun CuratedEvent.toCityEvent(nowMs: Long): CityEvent? {
    val start = isoToEpochMs(startAt) ?: return null
    val end = endAt?.let(::isoToEpochMs) ?: (start + 3 * HOUR_MS)
    if (end <= nowMs) return null
    return CityEvent(id = id, lat = lat, lng = lng, title = title.take(120), category = category,
        status = if (nowMs >= start) "live" else "soon", startAt = startAt, endAt = endAt.orEmpty(), visibility = "public")
}

/** "5 декабря · 19:00" in Astana time. */
internal fun CuratedEvent.whenLabel(): String {
    val local = (isoToEpochMs(startAt) ?: return "") + ASTANA_OFFSET_MS
    val days = local.floorDiv(86_400_000L)
    val (_, month, day) = civilFromDays(days)
    val minutes = (local - days * 86_400_000L) / 60_000L
    return "$day ${MONTHS[month - 1]} · ${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}

internal fun CuratedEvent.mapUrl(): String {
    fun f(v: Double) = ((v * 1e6).roundToLong() / 1e6).toString()
    return "https://2gis.kz/astana?m=${f(lng)}%2C${f(lat)}%2F17"
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + (if (month <= 2) 1 else 0)).toInt()
    return Triple(year, month, day)
}
