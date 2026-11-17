package com.eventmap.map.city

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

/** Current Astana weather from Open-Meteo (no key; CC BY 4.0, credit shown in the «Вид» sheet). */
internal const val LIVE_WEATHER_URL =
    "https://api.open-meteo.com/v1/forecast?latitude=51.1694&longitude=71.4491&current=temperature_2m,weather_code"
internal const val LIVE_WEATHER_CREDIT = "Open-Meteo.com (CC BY 4.0)"

internal data class LiveWeather(val kind: String, val temp: Int?)

/** WMO weather code → the scene's weather kinds (clear, cloudy, rain, snow). */
internal fun weatherKind(code: Int): String = when {
    code <= 1 -> "clear"
    code <= 48 -> "cloudy"
    code in 71..77 || code == 85 || code == 86 -> "snow"
    else -> "rain" // drizzle, rain, freezing rain, showers, thunderstorms
}

internal fun parseLiveWeather(raw: String): LiveWeather? {
    return try {
        val current = Json.parseToJsonElement(raw).jsonObject["current"]?.jsonObject ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val t = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
        LiveWeather(weatherKind(code), t?.takeIf { abs(it) < 70 }?.roundToInt())
    } catch (_: Exception) { null }
}

internal suspend fun fetchLiveWeather(client: HttpClient): LiveWeather? = try {
    val response = client.get(LIVE_WEATHER_URL)
    if (response.status == HttpStatusCode.OK) parseLiveWeather(response.bodyAsText().take(20_000)) else null
} catch (e: CancellationException) { throw e } catch (_: Exception) { null }

internal fun formatTemp(t: Int?): String = when {
    t == null -> ""
    t > 0 -> "+$t°"
    t < 0 -> "−${-t}°"
    else -> "0°"
}
