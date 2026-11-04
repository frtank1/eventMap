package com.eventmap.server

import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.repository.RefreshTokenRecord
import com.eventmap.server.domain.repository.RefreshTokenRepository
import com.eventmap.server.domain.repository.UserRepository
import java.util.UUID

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>() // key = email

    override suspend fun byEmail(email: String): User? = users[email]
    override suspend fun byId(id: String): User? = users.values.find { it.id == id }
    override suspend fun byIds(ids: List<String>): List<User> = users.values.filter { it.id in ids }
    override suspend fun searchByName(q: String, limit: Int): List<User> =
        users.values.filter { it.name.contains(q, ignoreCase = true) }.take(limit)
    override suspend fun updateProfile(id: String, displayName: String?, bio: String?): User? {
        val user = byId(id) ?: return null
        val updated = user.copy(name = displayName ?: user.name, bio = bio ?: user.bio)
        users[user.email] = updated
        return updated
    }
    override suspend fun saveInterests(id: String, interests: List<String>): Boolean {
        val user = byId(id) ?: return false
        users[user.email] = user.copy(interests = interests)
        return true
    }

    override suspend fun create(email: String, name: String, passwordHash: String): User {
        val user = User(UUID.randomUUID().toString(), email, name, passwordHash)
        users[email] = user
        return user
    }
}

class FakeRefreshTokenRepository : RefreshTokenRepository {
    val records = mutableMapOf<String, RefreshTokenRecord>() // key = tokenHash

    override suspend fun save(tokenHash: String, userId: String, expiresAtMs: Long) {
        records[tokenHash] = RefreshTokenRecord(tokenHash, userId, expiresAtMs, revoked = false)
    }

    override suspend fun find(tokenHash: String): RefreshTokenRecord? = records[tokenHash]

    override suspend fun revoke(tokenHash: String) {
        records[tokenHash]?.let { records[tokenHash] = it.copy(revoked = true) }
    }

    override suspend fun revokeAllForUser(userId: String) {
        records.replaceAll { _, r -> if (r.userId == userId) r.copy(revoked = true) else r }
    }
}
