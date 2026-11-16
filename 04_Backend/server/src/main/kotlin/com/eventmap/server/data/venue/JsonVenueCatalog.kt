package com.eventmap.server.data.venue

import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.repository.VenueCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Reviewed public data, deployed atomically with the backend, never user-supplied file paths. */
class JsonVenueCatalog(
    catalogJson: String = resource("venues/catalog.json"),
    worldJson: String = resource("world/manifest.json"),
) : VenueCatalog {
    @Serializable private data class Pack(val schemaVersion: Int, val buildings: List<VenueBuilding>)
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val buildings: Map<String, VenueBuilding>
    init {
        require(catalogJson.toByteArray().size <= 8 * 1024 * 1024) { "Venue catalog too large" }
        val pack = json.decodeFromString<Pack>(catalogJson)
        require(pack.schemaVersion == 1 && pack.buildings.size <= 500)
        require(pack.buildings.map { it.buildingId }.distinct().size == pack.buildings.size)
        require(pack.buildings.all { it.isValid() && json.encodeToString(VenueBuilding.serializer(), it).toByteArray().size <= 1024 * 1024 })
        val world = Json.parseToJsonElement(worldJson).jsonObject
        val defaults = world.getValue("models").jsonArray.map { item ->
            val row = item.jsonObject
            VenueBuilding(buildingId="landmark:" + row.getValue("id").jsonPrimitive.content,
                revision="unmapped-1", name=row.getValue("title").jsonPrimitive.content)
        }
        require(defaults.all { it.isValid() })
        buildings = defaults.associateBy { it.buildingId } + pack.buildings.associateBy { it.buildingId }
    }
    fun all(): List<VenueBuilding> = buildings.values.toList()
    override suspend fun building(id: String): VenueBuilding? {
        if (!validBuildingId(id)) return null
        return buildings[id] ?: if (id.startsWith("osm:")) VenueBuilding(buildingId=id, revision="unmapped-1", name="Здание") else null
    }
    companion object {
        private fun resource(path: String) = JsonVenueCatalog::class.java.classLoader
            .getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Missing venue resource: $path")
    }
}
