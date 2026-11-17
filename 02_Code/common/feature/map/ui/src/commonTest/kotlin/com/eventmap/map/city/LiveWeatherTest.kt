package com.eventmap.map.city

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveWeatherTest {
    @Test fun codesMapToSceneWeather() {
        assertEquals("clear", weatherKind(0)); assertEquals("cloudy", weatherKind(3)); assertEquals("cloudy", weatherKind(45))
        assertEquals("rain", weatherKind(61)); assertEquals("rain", weatherKind(95)); assertEquals("snow", weatherKind(73)); assertEquals("snow", weatherKind(86))
    }
    @Test fun parsesOpenMeteoCurrent() {
        assertEquals(LiveWeather("snow", -8), parseLiveWeather("""{"current":{"time":"2026-11-17T12:00","temperature_2m":-7.6,"weather_code":71}}"""))
        assertNull(parseLiveWeather("""{"current":{}}""")); assertNull(parseLiveWeather("not json"))
        assertEquals("−8°", formatTemp(-8)); assertEquals("+3°", formatTemp(3)); assertEquals("", formatTemp(null))
    }
}
