package com.eventmap.server

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.GeoPoint
import com.eventmap.server.domain.model.Money
import com.eventmap.server.domain.usecase.validationError
import kotlin.test.*

class EventValidationTest {
    private val event = Event("test", "Football", category = "Спорт", startAt = "2030-06-01T15:00:00Z",
        location = GeoPoint(51.1, 71.4, "Field"))

    @Test fun acceptsValidEvent() { assertNull(event.validationError()) }
    @Test fun rejectsInvalidCoordinates() {
        listOf(GeoPoint(Double.NaN, 71.4, "Field"), GeoPoint(91.0, 71.4, "Field"),
            GeoPoint(51.1, 181.0, "Field")).forEach { assertNotNull(event.copy(location = it).validationError()) }
    }
    @Test fun rejectsTimeWithoutZoneAndEndBeforeStart() {
        assertNotNull(event.copy(startAt = "2030-06-01T20:00:00").validationError())
        assertNotNull(event.copy(endAt = "2030-06-01T14:00:00Z").validationError())
    }
    @Test fun gatheringRequiresCapacity() {
        assertNotNull(event.copy(kind = "gathering").validationError())
        assertNull(event.copy(kind = "gathering", capacity = 10).validationError())
    }
    @Test fun rejectsInvalidRulesAndPrice() {
        assertNotNull(event.copy(visibility = "unknown").validationError())
        assertNotNull(event.copy(joinPolicy = "unknown").validationError())
        assertNotNull(event.copy(price = Money("free", 100)).validationError())
        assertNotNull(event.copy(price = Money("paid", 0)).validationError())
    }
    @Test fun liveSignalMustExpireInNextDay() {
        assertNotNull(event.copy(kind = "live_signal", expiresAtMs = 1000).validationError(nowMs = 2000))
        assertNotNull(event.copy(kind = "live_signal", expiresAtMs = 90_000_000).validationError(nowMs = 2000))
        assertNull(event.copy(kind = "live_signal", expiresAtMs = 3000).validationError(nowMs = 2000))
    }
}
