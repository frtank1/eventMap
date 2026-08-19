package com.eventmap.server.data.repository

import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class UserRepositoryImpl : UserRepository {
    override suspend fun byEmail(email: String): User? = tx {
        UsersTable.selectAll().where { UsersTable.email eq email }.map(::toUser).singleOrNull()
    }

    override suspend fun create(email: String, name: String, passwordHash: String): User = tx {
        val uid = UUID.randomUUID().toString()
        // @username по умолчанию — из email до @ (можно сменить в профиле)
        val username = email.substringBefore("@").take(64)
        UsersTable.insert {
            it[id] = uid; it[UsersTable.email] = email
            it[UsersTable.name] = name; it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.username] = username
        }
        User(uid, email, name, passwordHash, username = username)
    }

    override suspend fun byId(id: String): User? = tx {
        UsersTable.selectAll().where { UsersTable.id eq id }.map(::toUser).singleOrNull()
    }

    override suspend fun byIds(ids: List<String>): List<User> = tx {
        if (ids.isEmpty()) emptyList()
        else UsersTable.selectAll().where { UsersTable.id inList ids }.map(::toUser)
    }

    // Поиск G2 (люди): по имени и @username
    override suspend fun searchByName(q: String, limit: Int): List<User> = tx {
        val pattern = "%${q.lowercase()}%"
        UsersTable.selectAll()
            .where { (UsersTable.name.lowerCase() like pattern) or (UsersTable.username.lowerCase() like pattern) }
            .limit(limit)
            .map(::toUser)
    }

    override suspend fun updateProfile(id: String, displayName: String?, bio: String?): User? = tx {
        val updated = UsersTable.update({ UsersTable.id eq id }) { row ->
            displayName?.let { row[name] = it.take(255) }
            bio?.let { row[UsersTable.bio] = it }
        }
        if (updated == 0) null
        else UsersTable.selectAll().where { UsersTable.id eq id }.map(::toUser).singleOrNull()
    }

    override suspend fun saveInterests(id: String, interests: List<String>): Boolean = tx {
        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.interests] = interests.joinToString(",") { s -> s.trim() }.take(2000)
        } > 0
    }

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun toUser(r: org.jetbrains.exposed.sql.ResultRow) = User(
        id = r[UsersTable.id],
        email = r[UsersTable.email],
        name = r[UsersTable.name],
        passwordHash = r[UsersTable.passwordHash],
        username = r[UsersTable.username],
        bio = r[UsersTable.bio],
        avatarUrl = r[UsersTable.avatarUrl],
        city = r[UsersTable.city],
        interests = r[UsersTable.interests].split(",").map { it.trim() }.filter { it.isNotEmpty() },
    )
}
