package com.eventmap.map.city

import com.eventmap.event.models.*
import kotlinx.serialization.encodeToString
import kotlin.test.*

class VenueBrowserTest {
    private val room=VenueRoom("r1","Атриум",listOf(PlanPoint(0.0,0.0),PlanPoint(10.0,0.0),PlanPoint(10.0,10.0),PlanPoint(0.0,10.0)))
    private val floor=VenueFloor("l1","Первый",1,VenuePlan(100.0,100.0),listOf(room))
    private val building=VenueBuilding(buildingId="landmark:mega",revision="r1",name="Test",floors=listOf(floor))
    @Test fun geometryIsInertBoundedAndRoomPickingIsLocal() {
        assertTrue(building.isValid());assertEquals("r1",roomAt(floor,PlanPoint(5.0,5.0)))
        assertNull(roomAt(floor,PlanPoint(50.0,50.0)))
        assertFalse(building.copy(floors=listOf(floor.copy(plan=null))).isValid())
        assertFalse(building.copy(floors=List(33){floor.copy(id="l$it")}).isValid())
        assertFalse(building.copy(floors=listOf(floor.copy(plan=VenuePlan(5.0,5.0)))).isValid())
        assertTrue(building.accepts(EventVenue("landmark:mega","l1","r1")))
        assertFalse(building.accepts(EventVenue("landmark:mega","l2","r1")))
        assertFalse(EventVenue("landmark:mega",roomId="r1").structureValid())
    }
    @Test fun unknownFloorRemainsInBuildingListNotAnotherFloor() {
        fun event(id:String,v:EventVenue?)=EventDetail(id,id,venue=v)
        val all=listOf(event("building",EventVenue("landmark:mega")),event("room",EventVenue("landmark:mega","l1","r1")),
            event("removed",EventVenue("landmark:mega","old")),event("nearby",null),event("other",EventVenue("landmark:arena")),
            event("private",EventVenue("landmark:mega")).copy(visibility="invite"),
            event("ended",EventVenue("landmark:mega")).copy(status="ended"),
            event("expired",EventVenue("landmark:mega")).copy(expiresAtMs=1))
        assertEquals(listOf("building","room","removed"),eventsForVenue(all,EventVenue("landmark:mega"),now=2).map{it.id})
        assertEquals(listOf("room"),eventsForVenue(all,EventVenue("landmark:mega","l1"),now=2).map{it.id})
        assertTrue(venueLocationLabel(all[2].venue,building).contains("нет в загруженном плане"))
    }
    @Test fun buildingIdentityCrossesBridgeButNeverChangesMeetingCoordinates() {
        val place=CityMessage(v=1,type="place",id="mega",title="MEGA",lat=51.1,lng=71.4,geometryKind="stylized",buildingId="landmark:mega")
        val parsed=parseCityMessage(cityJson.encodeToString(place))!!
        assertEquals(EventVenue("landmark:mega"),parsed.creationPoint().venue)
        assertEquals(51.1,parsed.creationPoint().lat)
        assertNull(parseCityMessage(cityJson.encodeToString(place.copy(geometryKind="point"))))
        assertNull(parseCityMessage(cityJson.encodeToString(place.copy(buildingId="tile:42"))))
    }
}
