package com.eventmap.core.datastore

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class UserType {
    INTERNAL,
    EXTERNAL,
    NONE
}

@Serializable
data class AuthTokens(
    val accessToken: String = "",
    val refreshToken: String = ""
)

private const val KEY_ACTIVE = "active_user_type"
private const val KEY_INTERNAL = "internal_tokens"
private const val KEY_EXTERNAL = "external_tokens"

class TokenStorage(
    private val storage: SecureStorage
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun readActive(): UserType =
        storage.getString(KEY_ACTIVE)
            ?.let { runCatching { UserType.valueOf(it) }.getOrNull() }
            ?: UserType.NONE

    private fun writeActive(type: UserType) = storage.putString(KEY_ACTIVE, type.name)

    private fun readTokens(key: String): AuthTokens =
        storage.getString(key)
            ?.let { runCatching { json.decodeFromString<AuthTokens>(it) }.getOrNull() }
            ?: AuthTokens()

    private fun writeTokens(key: String, tokens: AuthTokens) =
        storage.putString(key, json.encodeToString(tokens))

    fun setActiveUser(userType: UserType) = writeActive(userType)

    fun getActiveUser(): UserType = readActive()

    fun saveTokens(userType: UserType, access: String, refresh: String) {
        when (userType) {
            UserType.INTERNAL -> writeTokens(KEY_INTERNAL, AuthTokens(access, refresh))
            UserType.EXTERNAL -> writeTokens(KEY_EXTERNAL, AuthTokens(access, refresh))
            UserType.NONE -> Unit
        }
        writeActive(userType)
    }

    fun getActiveAccessToken(): String = getActiveSession()?.accessToken ?: ""

    fun getActiveSession(): AuthTokens? = when (readActive()) {
        UserType.INTERNAL -> readTokens(KEY_INTERNAL)
        UserType.EXTERNAL -> readTokens(KEY_EXTERNAL)
        UserType.NONE -> null
    }

    fun isAuthorized(): Boolean = getActiveAccessToken().isNotBlank()

    fun logout() {
        when (readActive()) {
            UserType.INTERNAL -> writeTokens(KEY_INTERNAL, AuthTokens())
            UserType.EXTERNAL -> writeTokens(KEY_EXTERNAL, AuthTokens())
            UserType.NONE -> Unit
        }
        writeActive(UserType.NONE)
    }
}
