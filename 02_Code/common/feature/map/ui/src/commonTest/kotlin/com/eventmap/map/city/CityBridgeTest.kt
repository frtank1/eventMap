package com.eventmap.map.city
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
class CityBridgeTest {
    private val entrance = CityEntrance("node/123",51.1281,71.4302,CityEntranceKind.MAIN,CityWheelchair.UNKNOWN)
    private fun mappedPlace() = CityMessage(v=1,type="place",id="building-1",title="Библиотека",lat=51.128,lng=71.43,
        address="улица Достык, 11",addressStatus="source",addressSource="OpenStreetMap",sourceRef="osm:way/230358855",geometryKind="footprint",
        entranceStatus=CityEntranceStatus.MAPPED,entrances=listOf(entrance))

    @Test fun oldV1DefaultsToUnreviewedAndNeverInventsAnEntrance() {
        val p=parseCityMessage("""{"v":1,"type":"place","title":"Точка на карте","lat":51.128,"lng":71.43}""")!!
        assertEquals(CityEntranceStatus.UNREVIEWED,p.entranceStatus)
        assertTrue(p.entrances.isEmpty())
        assertEquals(CityCreationPoint(51.128,71.43,"Точка на карте",null),p.creationPoint())
        assertTrue(p.creationPoint().selectionLabel().contains("Вход не выбран"))
        for(status in listOf(CityEntranceStatus.NOT_MAPPED,CityEntranceStatus.UNREVIEWED,CityEntranceStatus.UNAVAILABLE)) {
            val missing=p.copy(entranceStatus=status)
            assertEquals(missing,parseCityMessage(cityJson.encodeToString(missing)))
            assertTrue(missing.entranceStatusLabel().isNotBlank())
            assertNull(parseCityMessage(cityJson.encodeToString(missing.copy(entrances=listOf(entrance)))))
        }
        assertNull(parseCityMessage(cityJson.encodeToString(p.copy(entranceStatus=CityEntranceStatus.MAPPED))))
    }

    @Test fun mappedEntrancesRoundTripAllEnumsAndInclusiveBounds() {
        val entries=(0..7).map { i -> entrance.copy(id="node/${i+1}",
            lat=if(i%2==0)50.9 else 51.4,lng=if(i%2==0)71.1 else 71.9,
            kind=CityEntranceKind.entries[i%5],wheelchair=CityWheelchair.entries[i%4]) }
        val p=mappedPlace().copy(entrances=entries)
        val raw=cityJson.encodeToString(p)
        assertEquals(p,parseCityMessage(raw))
        assertTrue(raw.contains("\"entranceStatus\":\"mapped\""))
        assertTrue(raw.contains("\"kind\":\"main\""))
        assertTrue(p.entranceStatusLabel().contains("не официальные"))
        assertTrue(entrance.label().contains("Коляска по данным OSM: неизвестно"))
    }

    @Test fun rejectsInvalidEntranceCoordinatesIdsCountsAndLengths() {
        val p=mappedPlace()
        val invalid=listOf(entrance.copy(lat=50.8999),entrance.copy(lat=51.4001),entrance.copy(lng=71.0999),entrance.copy(lng=71.9001),
            entrance.copy(id=""),entrance.copy(id="node/0"),entrance.copy(id="way/123"),entrance.copy(id="node/-1"),
            entrance.copy(id="node/1\n"),entrance.copy(id="node/"+"1".repeat(96)))
        for(e in invalid)assertNull(parseCityMessage(cityJson.encodeToString(p.copy(entrances=listOf(e)))),e.toString())
        assertNotNull(parseCityMessage(cityJson.encodeToString(p.copy(entrances=listOf(entrance.copy(id="node/"+"1".repeat(95)))))))
        assertNull(parseCityMessage(cityJson.encodeToString(p.copy(entrances=listOf(entrance,entrance)))))
        assertNull(parseCityMessage(cityJson.encodeToString(p.copy(entrances=(1..9).map { entrance.copy(id="node/$it") }))))
    }

    @Test fun rejectsMalformedEntrancePayloadInsteadOfCoercingOrDroppingItems() {
        val p=cityJson.parseToJsonElement(cityJson.encodeToString(mappedPlace())).jsonObject
        val e=p["entrances"]!!.jsonArray[0].jsonObject
        fun check(value:JsonElement) = assertNull(parseCityMessage(JsonObject(p+ ("entrances" to value)).toString()),value.toString())
        for(value in listOf(JsonNull,JsonObject(emptyMap()),JsonPrimitive("[]"),JsonArray(listOf(JsonNull))))check(value)
        for(key in listOf("id","lat","lng","kind","wheelchair")) {
            check(JsonArray(listOf(JsonObject(e-key))))
            check(JsonArray(listOf(JsonObject(e+(key to JsonNull)))))
        }
        for((key,value) in listOf("lat" to JsonPrimitive("51.1281"),"lng" to JsonPrimitive(true),
            "kind" to JsonPrimitive("official"),"wheelchair" to JsonPrimitive("accessible"),"id" to JsonPrimitive(123))) {
            check(JsonArray(listOf(JsonObject(e+(key to value)))))
        }
        for(status in listOf(JsonNull,JsonPrimitive("official"),JsonPrimitive(1)))
            assertNull(parseCityMessage(JsonObject(p+("entranceStatus" to status)).toString()))
        val raw=p.toString()
        for(value in listOf("NaN","Infinity","-Infinity","1e309","\"NaN\"","+51.1281","051.1281","51.1281f"))
            assertNull(parseCityMessage(raw.replace("51.1281",value)))
        assertNull(parseCityMessage(raw.replace("\"lat\":51.128,","\"lat\":\"51.128\",")))
        assertNull(parseCityMessage(raw.dropLast(1)))
    }

    @Test fun entrancePayloadRetainsTheTotal4096CharacterLimit() {
        val raw=cityJson.encodeToString(mappedPlace())
        assertNotNull(parseCityMessage(raw.padEnd(4096,' ')))
        assertNull(parseCityMessage(raw.padEnd(4097,' ')))
    }

    @Test fun creationRequiresExplicitSelectionAndPreservesIdentityAndOriginalPoint() {
        val p=mappedPlace()
        val original=p.creationPoint()
        assertEquals(CityCreationPoint(p.lat!!,p.lng!!,p.locationLabel(),null),original)
        val chosen=p.creationPoint(entrance.id)
        assertEquals(entrance.lat,chosen.lat);assertEquals(entrance.lng,chosen.lng)
        assertEquals(entrance.id,chosen.entranceId)
        assertEquals(p.locationLabel()+" · вход OSM node/123",chosen.locationLabel)
        assertEquals(original,p.creationPoint(null)) // deselection
        assertEquals(original,p.creationPoint("node/999")) // stale/foreign ID
        assertEquals(mappedPlace(),p) // no mutation of identity, address or geometry
        assertEquals(51.128,p.lat)
        val next=p.copy(id="another-building",lat=51.13,entrances=listOf(entrance.copy(id="node/456")))
        assertEquals(51.13,next.creationPoint(entrance.id).lat)
        assertNull(next.creationPoint(entrance.id).entranceId)
    }

    @Test fun entranceNoteNeverTruncatesTheExistingLocationLabel() {
        val p=mappedPlace().copy(address="a".repeat(202),title="t".repeat(120))
        assertEquals(255,p.locationLabel().length)
        assertEquals(p.locationLabel(),p.creationPoint(entrance.id).locationLabel)
        assertEquals(entrance.lat,p.creationPoint(entrance.id).lat)
        val note=" · вход OSM node/123"
        val exact=p.copy(title="t".repeat(255-note.length-205))
        assertEquals(exact.locationLabel()+note,exact.creationPoint(entrance.id).locationLabel)
        assertEquals(255,exact.creationPoint(entrance.id).locationLabel.length)
    }

    @Test fun addressesSurviveSelectionAndCreationWithoutInventingMissingData() {
        val p=CityMessage(v=1,type="place",title="Здание",lat=51.128,lng=71.43,address="улица Достык, 11",addressStatus="source",addressSource="OpenStreetMap",sourceRef="osm:way/230358855",geometryKind="footprint")
        assertEquals(p,parseCityMessage(cityJson.encodeToString(p)))
        assertEquals("улица Достык, 11",p.locationLabel())
        assertEquals("улица Достык, 11 · Библиотека",p.copy(title="Библиотека").locationLabel())
        assertNull(parseCityMessage(cityJson.encodeToString(p.copy(address=""))))
        assertNull(parseCityMessage(cityJson.encodeToString(p.copy(address="x\nmalformed"))))
        assertTrue(p.copy(address="",addressStatus="ambiguous").addressLabel().contains("несколько"))
    }
    @Test fun coverRequestsAreBoundedAndContainIdsNotUrls() {
        assertEquals(listOf("e1"),parseCityMessage("""{"v":1,"type":"covers","ids":["e1"]}""")!!.ids)
        assertNull(parseCityMessage("""{"v":1,"type":"covers","ids":["e1","e1"]}"""))
        assertNull(parseCityMessage(cityJson.encodeToString(CityMessage(v=1,type="covers",ids=(1..9).map { "e$it" }))))
    }
    @Test fun eventVisualsUseServerStateWithoutInventedCounters() {
        val e=CityEvent("gather",51.128,71.43,"Игра",kind="gathering",capacity=6,goingCount=4,confirmationCount=3,startAt="2026-12-01T10:00:00Z")
        val json=cityJson.parseToJsonElement(cityJson.encodeToString(e)).jsonObject
        assertEquals(6,json["capacity"]!!.jsonPrimitive.int);assertEquals(4,json["goingCount"]!!.jsonPrimitive.int)
        assertEquals(3,json["confirmationCount"]!!.jsonPrimitive.int);assertEquals("gathering",json["kind"]!!.jsonPrimitive.content)
        assertFalse("liveViewers" in json);assertFalse("friends" in json);assertFalse("token" in json)
    }
    @Test fun productionSnapshotHasPublicEventLabelsButNoPreviewOrCredentials() {
        val snapshot=CitySnapshot(manifestUrl="https://example.com/world/manifest.json",
            events=listOf(CityEvent("a",51.128,71.43,"Футбол",goingCount=4,endAt="2026-12-01T12:00:00Z")),
            night=false,theme="default",active=true,interactionBlocked=true)
        val json=cityJson.parseToJsonElement(cityJson.encodeToString(snapshot)).jsonObject
        assertFalse("preview" in json); assertFalse("token" in json)
        assertEquals("Футбол",json["events"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertTrue(json["interactionBlocked"]!!.jsonPrimitive.boolean)
    }
    @Test fun rejectsUnknownOrInvalidMessages() {
        for(raw in listOf("{}","[]","{\"v\":1,\"type\":\"auth\"}","{\"v\":1,\"type\":\"event\"}","{\"v\":1,\"type\":\"place\",\"title\":\"x\",\"lat\":0,\"lng\":0}","x".repeat(5000))) assertNull(parseCityMessage(raw))
    }
    @Test fun acceptsSelectedPlaceAndPublicEventsOnly() {
        val p=parseCityMessage("{\"v\":1,\"type\":\"place\",\"id\":\"baiterek\",\"title\":\"Байтерек\",\"lat\":51.12822,\"lng\":71.43056}")!!
        assertEquals(51.12822,p.lat);assertNotNull(parseCityMessage("{\"v\":1,\"type\":\"event\",\"id\":\"e1\"}"))
        assertTrue(distanceMeters(51.1,71.4,51.1,71.4)<1);assertTrue(distanceMeters(51.1,71.4,51.101,71.4) in 110.0..112.0)
    }
}
