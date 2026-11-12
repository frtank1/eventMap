package com.eventmap.map.city
import kotlin.test.*
class CityBridgeTest {
    @Test fun rejectsUnknownOrInvalidMessages() {
        for(raw in listOf("{}","[]","{\"v\":1,\"type\":\"auth\"}","{\"v\":1,\"type\":\"event\"}","{\"v\":1,\"type\":\"place\",\"title\":\"x\",\"lat\":0,\"lng\":0}","x".repeat(5000))) assertNull(parseCityMessage(raw))
    }
    @Test fun acceptsSelectedPlaceAndPublicEventsOnly() {
        val p=parseCityMessage("{\"v\":1,\"type\":\"place\",\"id\":\"baiterek\",\"title\":\"Байтерек\",\"lat\":51.12822,\"lng\":71.43056}")!!
        assertEquals(51.12822,p.lat);assertNotNull(parseCityMessage("{\"v\":1,\"type\":\"event\",\"id\":\"e1\"}"))
        assertTrue(distanceMeters(51.1,71.4,51.1,71.4)<1);assertTrue(distanceMeters(51.1,71.4,51.101,71.4) in 110.0..112.0)
    }
}
