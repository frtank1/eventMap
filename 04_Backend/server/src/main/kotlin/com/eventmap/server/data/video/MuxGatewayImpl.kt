package com.eventmap.server.data.video

import com.eventmap.server.config.AppConfig
import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** No credentials or provider responses are included in exception messages/logs. */
class MuxGatewayImpl(
    private val config: AppConfig,
    private val endpoint: String = "https://api.mux.com",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : MuxGateway {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val signingKey by lazy { runCatching { parsePrivateKey(config.muxSigningPrivateKey) }.getOrNull() }
    override val configured: Boolean get() = config.muxTokenId.isNotBlank() && config.muxTokenSecret.isNotBlank() &&
        config.muxWebhookSecret.isNotBlank() && config.muxSigningKeyId.isNotBlank() && signingKey != null

    override suspend fun createUpload(mediaId: String): MuxUpload = withContext(Dispatchers.IO) {
        if (!configured) throw MediaFailure(503, "VIDEO_OFF", "Видеосервис не настроен")
        val body = buildJsonObject {
            put("timeout", 3600)
            putJsonObject("new_asset_settings") {
                putJsonArray("playback_policies") { add("signed") }
                put("video_quality", "basic")
                put("passthrough", mediaId)
            }
        }
        val auth = Base64.getEncoder().encodeToString("${config.muxTokenId}:${config.muxTokenSecret}".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("${endpoint.trimEnd('/')}/video/v1/uploads"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Basic $auth")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        catch (_: java.io.IOException) { throw providerFailure() }
        catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw providerFailure() }
        if (response.statusCode() !in 200..299) throw providerFailure()
        try {
            val data = json.parseToJsonElement(response.body()).jsonObject.getValue("data").jsonObject
            val id = data.getValue("id").jsonPrimitive.content
            val url = data.getValue("url").jsonPrimitive.content
            require(id.isNotBlank() && URI.create(url).scheme == "https")
            MuxUpload(id, url)
        } catch (_: Exception) { throw providerFailure() }
    }

    override fun playback(media: Media): Media {
        if (media.provider != "mux") return media
        val id = media.playbackId
        if (media.status != "ready" || id.isNullOrBlank()) return media.copy(url = "", thumbnailUrl = null)
        if (signingKey == null || config.muxSigningKeyId.isBlank()) return media.copy(url = "", thumbnailUrl = null, status = "unavailable")
        return media.copy(
            url = "https://stream.mux.com/$id.m3u8?token=${token(id, "v")}",
            thumbnailUrl = "https://image.mux.com/$id/thumbnail.jpg?token=${token(id, "t")}",
        )
    }

    private fun token(id: String, audience: String): String {
        val header = buildJsonObject { put("alg", "RS256"); put("typ", "JWT"); put("kid", config.muxSigningKeyId) }
        val claims = buildJsonObject {
            put("sub", id); put("aud", audience); put("kid", config.muxSigningKeyId)
            put("exp", nowSeconds() + 3600)
            if (audience == "t") put("width", 480)
        }
        val payload = "${encoder.encodeToString(header.toString().toByteArray())}.${encoder.encodeToString(claims.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(signingKey); update(payload.toByteArray()) }.sign()
        return "$payload.${encoder.encodeToString(signature)}"
    }

    override fun verifyWebhook(body: ByteArray, signature: String?): MuxEvent {
        if (config.muxWebhookSecret.isBlank()) throw MediaFailure(503, "VIDEO_OFF", "Webhook не настроен")
        if (!verifyMuxSignature(body, signature, config.muxWebhookSecret, nowSeconds()))
            throw MediaFailure(401, "BAD_SIGNATURE", "Недействительная подпись webhook")
        return try {
            val root = json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
            val type = root.string("type")!!
            val id = root.string("id")!!
            require(id.length in 1..255)
            val data = root.getValue("data").jsonObject
            val asset = type.startsWith("video.asset.")
            val signed = data["playback_ids"]?.jsonArray?.firstOrNull {
                it.jsonObject.string("policy") == "signed"
            }?.jsonObject?.string("id")
            MuxEvent(id, type, data.string("passthrough") ?: data["new_asset_settings"]?.jsonObject?.string("passthrough"),
                if (asset) data.string("upload_id") else data.string("id"),
                if (asset) data.string("id") else data.string("asset_id"), signed,
                data["duration"]?.jsonPrimitive?.doubleOrNull)
        } catch (_: Exception) { throw MediaFailure(400, "BAD_WEBHOOK", "Неверный формат webhook") }
    }

    private fun providerFailure() = MediaFailure(502, "VIDEO_PROVIDER_ERROR", "Видеосервис временно недоступен. Повторите позже")
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun verifyMuxSignature(body: ByteArray, header: String?, secret: String, now: Long): Boolean {
    if (header == null || secret.isBlank()) return false
    val fields = header.split(',').map { it.trim().split('=', limit = 2) }.filter { it.size == 2 }
    val timestamps = fields.filter { it[0] == "t" }
    if (timestamps.size != 1) return false
    val timestamp = timestamps.single()[1].toLongOrNull() ?: return false
    if (timestamp < now - 300 || timestamp > now + 300) return false
    val expected = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")); update("$timestamp.".toByteArray())
    }.doFinal(body)
    return fields.filter { it[0] == "v1" }.any { (_, hex) ->
        if (!hex.matches(Regex("[0-9a-fA-F]{64}"))) false
        else MessageDigest.isEqual(expected, hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    }
}

/** Mux dashboard gives base64-encoded PEM. Accept PKCS#1 and PKCS#8, also raw PEM. */
private fun parsePrivateKey(raw: String): java.security.PrivateKey {
    val normalized = raw.trim().replace("\\n", "\n")
    val pem = if (normalized.startsWith("-----BEGIN")) normalized
        else String(Base64.getDecoder().decode(normalized), Charsets.UTF_8)
    val bytes = Base64.getMimeDecoder().decode(pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString(""))
    val encoded = if (pem.contains("BEGIN RSA PRIVATE KEY")) {
        // Wrap PKCS#1 RSAPrivateKey in PKCS#8 PrivateKeyInfo with rsaEncryption OID.
        der(0x30, byteArrayOf(2, 1, 0) + byteArrayOf(0x30, 0x0d, 6, 9, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 1, 1, 1, 5, 0) + der(4, bytes))
    } else bytes
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded))
}

private fun der(tag: Int, value: ByteArray): ByteArray {
    val size = value.size
    val length = when {
        size < 128 -> byteArrayOf(size.toByte())
        size < 256 -> byteArrayOf(0x81.toByte(), size.toByte())
        else -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
    }
    return byteArrayOf(tag.toByte()) + length + value
}
