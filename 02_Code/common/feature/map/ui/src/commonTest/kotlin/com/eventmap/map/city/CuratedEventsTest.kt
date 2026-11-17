package com.eventmap.map.city

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CuratedEventsTest {
    private val event = CuratedEvent(id = "linda-30-nov", title = "Концерт", lat = 51.12, lng = 71.43,
        startAt = "2026-12-05T19:00:00+05:00", endAt = "2026-12-05T22:00:00+05:00", category = "music")

    @Test fun parsesIsoWithOffset() {
        assertEquals(1796479200000L, isoToEpochMs("2026-12-05T19:00:00+05:00"))
        assertEquals(0L, isoToEpochMs("1970-01-01T05:00+05:00"))
        assertEquals(0L, isoToEpochMs("1970-01-01T00:00:00Z"))
        assertNull(isoToEpochMs("5 декабря"))
    }

    @Test fun statusAndExpiry() {
        val start = isoToEpochMs(event.startAt)!!
        assertEquals("soon", event.toCityEvent(start - 1)?.status)
        assertEquals("live", event.toCityEvent(start + 60_000)?.status)
        assertNull(event.toCityEvent(start + 4 * 3_600_000L))
        assertNull(event.copy(endAt = null).toCityEvent(start + 3 * 3_600_000L))
    }

    @Test fun labelsAndNight() {
        assertEquals("5 декабря · 19:00", event.whenLabel())
        assertEquals("https://2gis.kz/astana?m=71.43%2C51.12%2F17", event.mapUrl())
        assertTrue(astanaNight(isoToEpochMs("2026-12-05T19:00:00+05:00")!!))
        assertFalse(astanaNight(isoToEpochMs("2026-12-05T12:00:00+05:00")!!))
    }

    @Test fun parsingKeepsOnlyAstanaEventsWithTimes() {
        val json = """[{"id":"a","title":"A","lat":51.1,"lng":71.4,"startAt":"2026-12-05T19:00:00+05:00"},
            {"id":"b","title":"B","lat":43.2,"lng":76.9,"startAt":"2026-12-05T19:00:00+05:00"},
            {"id":"c","title":"C","lat":51.1,"lng":71.4,"startAt":"soon"}]"""
        assertEquals(listOf("a"), parseCuratedEvents(json).map { it.id })
        assertTrue(parseCuratedEvents("not json").isEmpty())
    }
}
