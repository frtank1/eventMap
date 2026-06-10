package com.eventmap.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("expires_in")
    val expiresIn: Long,

    @SerialName("refresh_expires_in")
    val refreshExpiresIn: Long,

    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("token_type")
    val tokenType: String,

    @SerialName("scope")
    val scope: String
)
