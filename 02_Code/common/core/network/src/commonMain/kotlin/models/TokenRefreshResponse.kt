package com.eventmap.core.network.models

import kotlinx.serialization.Serializable

// Ответ EventMap /auth/refresh (JSON, camelCase). См. 05_Docs/API_CONTRACT.md.
@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresIn: Long = 0,
)

// Тело запроса на обновление токена: POST /auth/refresh { "refreshToken": "..." }
@Serializable
data class TokenRefreshRequest(
    val refreshToken: String,
)
