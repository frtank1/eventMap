package com.eventmap.server.data.db

import org.jetbrains.exposed.sql.Table

object OrganizersTable : Table("organizers") {
    val id = varchar("id", 64)
    val name = varchar("name", 255)
    val bio = text("bio").default("")
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val isVerified = bool("is_verified").default(false)
    val subscribers = integer("subscribers").default(0)
    override val primaryKey = PrimaryKey(id)
}

object EventsTable : Table("events") {
    val id = varchar("id", 64)
    val title = varchar("title", 255)
    val description = text("description").default("")
    val category = varchar("category", 64).default("")
    val status = varchar("status", 16).default("soon")
    val startAt = varchar("start_at", 40).default("")
    val endAt = varchar("end_at", 40).default("")
    val lat = double("lat").default(0.0)
    val lng = double("lng").default(0.0)
    val locationName = varchar("location_name", 255).default("")
    val priceType = varchar("price_type", 16).default("free")
    val priceAmount = integer("price_amount").default(0)
    val currency = varchar("currency", 8).default("KZT")
    val goingCount = integer("going_count").default(0)
    val liveViewers = integer("live_viewers").default(0)
    val ratingAvg = double("rating_avg").default(0.0)
    val ratingCount = integer("rating_count").default(0)
    val cover = varchar("cover", 512).nullable()
    val organizerId = varchar("organizer_id", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255).default("")
    val passwordHash = varchar("password_hash", 255)
    override val primaryKey = PrimaryKey(id)
}

// Храним SHA-256 хеш refresh-токена (сам токен — только у клиента).
object RefreshTokensTable : Table("refresh_tokens") {
    val tokenHash = varchar("token_hash", 64)
    val userId = varchar("user_id", 64).index()
    val expiresAtMs = long("expires_at_ms")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(tokenHash)
}
