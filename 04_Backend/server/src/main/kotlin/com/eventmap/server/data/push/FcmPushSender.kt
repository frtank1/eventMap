package com.eventmap.server.data.push

import com.eventmap.server.config.AppConfig
import com.eventmap.server.domain.repository.DeviceTokenRepository
import com.eventmap.server.domain.repository.NotificationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Push через Firebase Cloud Messaging HTTP v1.
 *
 * Без Firebase Admin SDK: авторизация — OAuth2 по service-account (RS256-JWT → access_token),
 * HTTP — встроенный java.net.http.HttpClient (JDK). APNS работает через ту же FCM (iOS-токены).
 * Если FCM_* не заданы — configured=false, отправка no-op (вернёт 0).
 *
 * Env (из service-account JSON): FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY.
 */
class FcmPushSender(
    private val cfg: AppConfig,
    private val tokens: DeviceTokenRepository,
) : NotificationPort {

    private val http: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Кэш access-токена (живёт ~1 час). Обновляем за минуту до истечения.
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAtMs: Long = 0

    override val configured: Boolean
        get() = cfg.fcmProjectId.isNotBlank() && cfg.fcmClientEmail.isNotBlank() && cfg.fcmPrivateKey.isNotBlank()

    override suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ): Int {
        if (!configured) return 0
        val deviceTokens = tokens.tokensFor(userId)
        if (deviceTokens.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            val access = accessToken() ?: return@withContext 0
            var ok = 0
            for (t in deviceTokens) {
                if (sendOne(access, t, title, body, data)) ok++
            }
            ok
        }
    }

    // --- FCM send ---

    private fun sendOne(accessToken: String, deviceToken: String, title: String, body: String, data: Map<String, String>): Boolean {
        val message = buildJsonObject {
            put("message", buildJsonObject {
                put("token", deviceToken)
                put("notification", buildJsonObject {
                    put("title", title)
                    put("body", body)
                })
                if (data.isNotEmpty()) {
                    put("data", buildJsonObject { data.forEach { (k, v) -> put(k, v) } })
                }
            })
        }
        val req = HttpRequest.newBuilder()
            .uri(URI("https://fcm.googleapis.com/v1/projects/${cfg.fcmProjectId}/messages:send"))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(message.toString()))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() in 200..299
    }

    // --- OAuth2 (service-account JWT → access_token) ---

    private fun accessToken(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMs) return it }

        val jwt = signServiceAccountJwt(now / 1000)
        val form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(jwt)
        val req = HttpRequest.newBuilder()
            .uri(URI("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) return null
        val obj: JsonObject = json.parseToJsonElement(resp.body()).jsonObject
        val token = (obj["access_token"] as? JsonPrimitive)?.content ?: return null
        val expiresIn = (obj["expires_in"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 3600L
        cachedToken = token
        tokenExpiresAtMs = now + (expiresIn - 60) * 1000
        return token
    }

    private fun signServiceAccountJwt(iatSeconds: Long): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = buildJsonObject {
            put("iss", cfg.fcmClientEmail)
            put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            put("aud", "https://oauth2.googleapis.com/token")
            put("iat", iatSeconds)
            put("exp", iatSeconds + 3600)
        }.toString()
        val signingInput = b64url(header.toByteArray(Charsets.UTF_8)) + "." + b64url(claims.toByteArray(Charsets.UTF_8))

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey())
        signer.update(signingInput.toByteArray(Charsets.UTF_8))
        val sig = signer.sign()
        return signingInput + "." + b64url(sig)
    }

    private fun privateKey(): java.security.PrivateKey {
        val pem = cfg.fcmPrivateKey
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(pem)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun enc(v: String): String = URLEncoder.encode(v, Charsets.UTF_8)
}
