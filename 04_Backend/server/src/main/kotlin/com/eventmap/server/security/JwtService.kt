package com.eventmap.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.eventmap.server.config.AppConfig
import java.util.Date
import java.util.UUID

class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    val accessTtlSec = 15 * 60L            // 15 минут (см. ARCHITECTURE.md)
    val refreshTtlSec = 30 * 24 * 3600L    // 30 дней

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .build()

    fun generateAccess(userId: String): String = JWT.create()
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + accessTtlSec * 1000))
        .sign(algorithm)

    // Refresh — с jti (уникальность) и сроком жизни. Валидируется по БД (см. RefreshTokenRepository).
    fun generateRefresh(userId: String): String = JWT.create()
        .withIssuer(config.jwtIssuer)
        .withClaim("userId", userId)
        .withClaim("type", "refresh")
        .withJWTId(UUID.randomUUID().toString())
        .withExpiresAt(Date(System.currentTimeMillis() + refreshTtlSec * 1000))
        .sign(algorithm)
}
