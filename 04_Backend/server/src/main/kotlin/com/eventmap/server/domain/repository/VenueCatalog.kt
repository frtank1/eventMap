package com.eventmap.server.domain.repository

import com.eventmap.server.domain.model.*

interface VenueCatalog {
    suspend fun building(id: String): VenueBuilding?
    suspend fun validate(venue: EventVenue?): String? {
        if (venue == null) return null
        if (!venue.structureValid()) return "Некорректная привязка здания, этажа или помещения"
        val building = building(venue.buildingId) ?: return "Здание не найдено в каталоге"
        return if (building.accepts(venue)) null else "Этаж или помещение изменились. Обновите план и выберите место заново"
    }
}
