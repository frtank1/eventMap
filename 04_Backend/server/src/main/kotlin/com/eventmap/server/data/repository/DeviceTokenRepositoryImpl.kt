package com.eventmap.server.data.repository

import com.eventmap.server.data.db.DeviceTokensTable
import com.eventmap.server.domain.repository.DeviceTokenRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class DeviceTokenRepositoryImpl : DeviceTokenRepository {

    private suspend fun <T> tx(block: () -> T) = newSuspendedTransaction(Dispatchers.IO) { block() }

    // upsert: токен уникален; при повторной регистрации обновляем владельца/платформу.
    override suspend fun register(userId: String, token: String, platform: String) = tx {
        val exists = !DeviceTokensTable.selectAll()
            .where { DeviceTokensTable.token eq token }
            .empty()
        if (exists) {
            DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.platform] = platform
            }
        } else {
            DeviceTokensTable.insert {
                it[DeviceTokensTable.token] = token
                it[DeviceTokensTable.userId] = userId
                it[DeviceTokensTable.platform] = platform
                it[createdAtMs] = System.currentTimeMillis()
            }
        }
        Unit
    }

    override suspend fun tokensFor(userId: String): List<String> = tx {
        DeviceTokensTable.selectAll()
            .where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }

    override suspend fun delete(token: String) = tx {
        DeviceTokensTable.deleteWhere { DeviceTokensTable.token eq token }
        Unit
    }
}
