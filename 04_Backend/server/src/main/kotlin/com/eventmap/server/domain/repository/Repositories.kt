package com.eventmap.server.domain.repository

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.User

interface EventRepository {
    suspend fun all(): List<Event>
    suspend fun byId(id: String): Event?
    suspend fun byOrganizer(organizerId: String): List<Event>
}

interface OrganizerRepository {
    suspend fun byId(id: String): Organizer?
}

interface UserRepository {
    suspend fun byEmail(email: String): User?
    suspend fun create(email: String, name: String, passwordHash: String): User
}
