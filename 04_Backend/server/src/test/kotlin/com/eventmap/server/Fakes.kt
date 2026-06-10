package com.eventmap.server

import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.repository.RefreshTokenRecord
import com.eventmap.server.domain.repository.RefreshTokenRepository
import com.eventmap.server.domain.repository.UserRepository
import java.util.UUID

class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>() // key = email

    override suspend fun byEmail(email: String): User? = users[email]

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
