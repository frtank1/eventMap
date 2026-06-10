package com.eventmap.auth.models

import kotlinx.serialization.Serializable

// Краткая карточка пользователя (см. UserShort в API_CONTRACT).
@Serializable
data class UserShort(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
)

// Результат успешной авторизации/регистрации.
@Serializable
data class AuthResult(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresIn: Long = 0,
    val user: UserShort = UserShort(),
)

// Жанр/интерес для экрана A8.
@Serializable
data class Interest(
    val id: String,
    val title: String,
    val emoji: String = "",
)
