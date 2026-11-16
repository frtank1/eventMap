package com.eventmap.event.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable data class EventVenue(val buildingId: String, val floorId: String? = null, val roomId: String? = null)
@Serializable data class PlanPoint(val x: Double, val y: Double)
@Serializable data class VenuePlan(val width: Double, val height: Double, val outline: List<PlanPoint> = emptyList())
@Serializable data class VenueRoom(val id: String, val name: String, val polygon: List<PlanPoint> = emptyList())
@Serializable data class VenueFloor(
    val id: String, val name: String, val order: Int, val plan: VenuePlan? = null,
    val rooms: List<VenueRoom> = emptyList(),
)
@Serializable data class VenueBuilding(
    val schemaVersion: Int = 1, val buildingId: String, val revision: String,
    val name: String, val floors: List<VenueFloor> = emptyList(),
)

fun validBuildingId(id: String): Boolean =
    Regex("(landmark:[a-z][a-z0-9-]{0,63}|osm:(way|relation):[1-9][0-9]{0,19})").matches(id)
private fun validPartId(id: String) = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}").matches(id)
private fun validVenueText(text: String, max: Int = 120) =
    text.isNotBlank() && text.length <= max && text.none { it.code < 32 || it.code == 127 }

fun EventVenue.structureValid(): Boolean = validBuildingId(buildingId) &&
    (floorId == null || validPartId(floorId)) && (roomId == null || (floorId != null && validPartId(roomId)))

/** Bounded, inert vectors only. No downloaded code, image URL, WebView markup or arbitrary paths. */
fun VenueBuilding.isValid(): Boolean {
    if (schemaVersion != 1 || !validBuildingId(buildingId) || !validVenueText(name) ||
        !validPartId(revision) || floors.size > 32 || floors.map { it.id }.distinct().size != floors.size ||
        floors.sumOf { it.rooms.size } > 1000) return false
    var vertices = 0
    for (floor in floors) {
        if (!validPartId(floor.id) || !validVenueText(floor.name) || floor.order !in -100..200 ||
            floor.rooms.size > 200 || floor.rooms.map { it.id }.distinct().size != floor.rooms.size) return false
        val plan = floor.plan
        if (plan != null && (!plan.width.isFinite() || !plan.height.isFinite() ||
            plan.width !in 1.0..10000.0 || plan.height !in 1.0..10000.0)) return false
        fun validPolygon(points: List<PlanPoint>): Boolean {
            vertices += points.size
            if (vertices > 20000) return false
            if (points.isEmpty()) return true
            if (plan == null || points.size !in 3..256 ||
                points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0.0..plan.width || it.y !in 0.0..plan.height }) return false
            return abs(points.indices.sumOf { i ->
                val a = points[i]; val b = points[(i + 1) % points.size]; a.x * b.y - b.x * a.y
            }) > .0001
        }
        if (plan != null && !validPolygon(plan.outline)) return false
        for (room in floor.rooms) {
            if (!validPartId(room.id) || !validVenueText(room.name) || !validPolygon(room.polygon)) return false
        }
    }
    return true
}

fun VenueBuilding.accepts(venue: EventVenue): Boolean = venue.structureValid() && venue.buildingId == buildingId &&
    (venue.floorId == null || floors.any { it.id == venue.floorId &&
        (venue.roomId == null || it.rooms.any { room -> room.id == venue.roomId }) })

