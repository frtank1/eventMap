package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.repository.RefreshTokenRepository
import com.eventmap.server.security.JwtService
import java.security.MessageDigest

data class SessionTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long)

internal fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

// Выпуск пары токенов: refresh сохраняем в БД (хеш) — иначе его нельзя отозвать.
class IssueTokensUseCase(
    private val jwt: JwtService,
    private val refreshTokens: RefreshTokenRepository,
) {
    suspend operator fun invoke(userId: String): SessionTokens {
        val refresh = jwt.generateRefresh(userId)
        refreshTokens.save(
            tokenHash = sha256(refresh),
            userId = userId,
            expiresAtMs = System.currentTimeMillis() + jwt.refreshTtlSec * 1000,
        )
        return SessionTokens(jwt.generateAccess(userId), refresh, jwt.accessTtlSec)
    }
}

// Ротация: старый refresh отзываем, выдаём новую пару. Невалидный/отозванный/протухший → null.
class RefreshSessionUseCase(
    private val refreshTokens: RefreshTokenRepository,
    private val issueTokens: IssueTokensUseCase,
) {
    suspend operator fun invoke(refreshToken: String): SessionTokens? {
        val hash = sha256(refreshToken)
        val record = refreshTokens.find(hash) ?: return null
        if (record.revoked || record.expiresAtMs < System.currentTimeMillis()) return null
        refreshTokens.revoke(hash)
        return issueTokens(record.userId)
    }
}

class LogoutUseCase(private val refreshTokens: RefreshTokenRepository) {
    suspend operator fun invoke(refreshToken: String) {
        refreshTokens.revoke(sha256(refreshToken))
    }
}
