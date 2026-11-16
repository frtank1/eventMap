package com.eventmap.server.domain.repository

import com.eventmap.server.domain.model.VenueBuilding
import kotlinx.serialization.Serializable

@Serializable data class VenueSummary(val buildingId: String, val name: String, val version: Long)
@Serializable data class VenueRevision(val revision: String, val publishedAtMs: Long)
@Serializable data class VenueEditState(
    val version: Long, val draft: VenueBuilding, val published: VenueBuilding?,
    val history: List<VenueRevision> = emptyList(),
)
class VenueEditError(val code: String, override val message: String) : RuntimeException(message)

interface VenueEditor : VenueCatalog {
    suspend fun list(): List<VenueSummary>
    suspend fun editor(id: String): VenueEditState?
    suspend fun save(id: String, expectedVersion: Long, building: VenueBuilding): VenueEditState
    suspend fun publish(id: String, expectedVersion: Long): VenueEditState
    suspend fun revision(id: String, revision: String): VenueBuilding?
}
