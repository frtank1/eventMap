package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.validBuildingId
import com.eventmap.server.domain.repository.VenueCatalog
import com.eventmap.server.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.venueRoutes(catalog: VenueCatalog) {
    get("/venues/{buildingId}") {
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.response.header("X-Content-Type-Options", "nosniff")
        val id = call.parameters["buildingId"].orEmpty()
        if (!validBuildingId(id)) return@get call.respond(HttpStatusCode.BadRequest,
            ErrorResponse(ErrorResponse.Body("INVALID_BUILDING", "Некорректный идентификатор здания")))
        val building = catalog.building(id) ?: return@get call.respond(HttpStatusCode.NotFound,
            ErrorResponse(ErrorResponse.Body("BUILDING_NOT_FOUND", "Здание не найдено")))
        call.respond(building)
    }
}
