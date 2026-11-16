package com.eventmap.event

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.VenueBuilding

interface VenueRepository {
    suspend fun getBuilding(id: String): KtorResponse<VenueBuilding>
    suspend fun getEvents(buildingId: String): KtorResponse<List<EventDetail>>
}
