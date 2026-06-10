package com.eventmap.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.eventmap.server.config.AppConfig
import java.util.Date

class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    val accessTtlSec = 3600L

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

    fun generateRefresh(userId: String): String = JWT.create()
        .withIssuer(config.jwtIssuer)
        .withClaim("userId", userId)
        .withClaim("type", "refresh")
        .sign(algorithm)
}
