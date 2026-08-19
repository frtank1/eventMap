package com.eventmap.core.network.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Минимальный разбор JWT — только для локальной проверки срока действия (claim `exp`).
 * Подпись НЕ проверяем: это делает бэкенд. Нам важно лишь не слать заведомо мёртвый
 * токен и вовремя сбросить пользователя на авторизацию.
 */
@OptIn(ExperimentalEncodingApi::class)
object Jwt {
    private val json = Json { ignoreUnknownKeys = true }

    // JWT payload — base64url без паддинга.
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** `exp` (Unix-секунды) из payload, либо null если токен не распарсился / claim отсутствует. */
    fun expiresAtSeconds(token: String): Long? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val payload = base64Url.decode(parts[1]).decodeToString()
            json.parseToJsonElement(payload).jsonObject["exp"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    /**
     * Токен просрочен (с запасом [leewaySeconds], чтобы не поймать 401 «в полёте»).
     * Если `exp` не распарсили — считаем НЕ просроченным, чтобы не разлогинивать зря.
     */
    fun isExpired(
        token: String,
        nowSeconds: Long = Clock.System.now().epochSeconds,
        leewaySeconds: Long = 30,
    ): Boolean {
        if (token.isBlank()) return true
        val exp = expiresAtSeconds(token) ?: return false
        return nowSeconds >= exp - leewaySeconds
    }
}
