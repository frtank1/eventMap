package com.eventmap.server.data.repository

import com.eventmap.server.data.db.RefreshTokensTable
import com.eventmap.server.domain.repository.RefreshTokenRecord
import com.eventmap.server.domain.repository.RefreshTokenRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class RefreshTokenRepositoryImpl : RefreshTokenRepository {

    override suspend fun save(tokenHash: String, userId: String, expiresAtMs: Long) =
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokensTable.insert {
                it[RefreshTokensTable.tokenHash] = tokenHash
                it[RefreshTokensTable.userId] = userId
                it[RefreshTokensTable.expiresAtMs] = expiresAtMs
            }
            Unit
        }

    override suspend fun find(tokenHash: String): RefreshTokenRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokensTable.selectAll().where { RefreshTokensTable.tokenHash eq tokenHash }
                .map {
                    RefreshTokenRecord(
                        tokenHash = it[RefreshTokensTable.tokenHash],
                        userId = it[RefreshTokensTable.userId],
                        expiresAtMs = it[RefreshTokensTable.expiresAtMs],
                        revoked = it[RefreshTokensTable.revoked],
                    )
                }
                .singleOrNull()
        }

    override suspend fun revoke(tokenHash: String) =
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq tokenHash }) { it[revoked] = true }
            Unit
        }

    override suspend fun revokeAllForUser(userId: String) =
        newSuspendedTransaction(Dispatchers.IO) {
            RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) { it[revoked] = true }
            Unit
        }
}
