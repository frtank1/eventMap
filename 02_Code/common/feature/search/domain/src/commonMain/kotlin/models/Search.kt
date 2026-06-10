package com.eventmap.search.models

import kotlinx.serialization.Serializable

enum class SearchType(val api: String, val label: String) {
    Events("events", "События"),
    Organizers("organizers", "Организаторы"),
    People("people", "Люди"),
    Genres("genres", "Жанры"),
}

@Serializable
data class SearchHit(
    val id: String,
    val type: String = "events",     // events | organizers | people | genres
    val title: String,
    val subtitle: String = "",
    val eventId: String? = null,     // для type=events — id события
    val emoji: String = "",
)
