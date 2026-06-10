package com.eventmap.event.data.ktor

import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import kotlinx.serialization.Serializable

// Тело POST /events (см. API_CONTRACT, создание по 3 шагам).
@Serializable
data class CreateEventRequest(
    val title: String,
    val description: String,
    val coverMediaId: String? = null,
    val location: EventLocation?,
    val startAt: String,
    val endAt: String,
    val category: String,
    val price: EventPrice,
    val limit: Int? = null,
)
