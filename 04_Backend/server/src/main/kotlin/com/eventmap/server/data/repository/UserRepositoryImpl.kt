package com.eventmap.server.data.repository

import com.eventmap.server.data.db.UsersTable
import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class UserRepositoryImpl : UserRepository {
    override suspend fun byEmail(email: String): User? = newSuspendedTransaction(Dispatchers.IO) {
        UsersTable.selectAll().where { UsersTable.email eq email }.map(::toUser).singleOrNull()
    }

    override suspend fun create(email: String, name: String, passwordHash: String): User =
        newSuspendedTransaction(Dispatchers.IO) {
            val uid = UUID.randomUUID().toString()
            UsersTable.insert {
                it[id] = uid; it[UsersTable.email] = email
                it[UsersTable.name] = name; it[UsersTable.passwordHash] = passwordHash
            }
            User(uid, email, name, passwordHash)
        }

    private fun toUser(r: org.jetbrains.exposed.sql.ResultRow) =
        User(r[UsersTable.id], r[UsersTable.email], r[UsersTable.name], r[UsersTable.passwordHash])
}
