package com.eventmap.server.data.storage

import com.eventmap.server.config.AppConfig
import com.eventmap.server.domain.repository.StoragePort
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * S3-совместимое хранилище (Cloudflare R2 / MinIO) через presigned-URL.
 *
 * Подпись AWS Signature V4 реализована на чистом JDK (javax.crypto) — без AWS SDK,
 * чтобы не тащить тяжёлую зависимость. Path-style адресация: <endpoint>/<bucket>/<key>.
 * Сервер выдаёт временную ссылку на PUT, клиент льёт файл напрямую в хранилище
 * (минуя сервер — большие видео/фото не нагружают Ktor).
 *
 * Env: S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY, S3_PUBLIC_BASE.
 */
class StorageService(private val cfg: AppConfig) : StoragePort {

    override val configured: Boolean
        get() = cfg.s3Endpoint.isNotBlank() && cfg.s3Bucket.isNotBlank() &&
            cfg.s3AccessKey.isNotBlank() && cfg.s3SecretKey.isNotBlank()

    private val endpoint: String get() = cfg.s3Endpoint.trimEnd('/')
    private val host: String get() = URI(endpoint).authority

    override fun publicUrl(key: String): String {
        val base = cfg.s3PublicBase.trimEnd('/')
        return if (base.isNotBlank()) "$base/${encodePath(key)}"
        else "$endpoint/${cfg.s3Bucket}/${encodePath(key)}"
    }

    /** Presigned PUT: SigV4 query-string auth, payload не подписываем (UNSIGNED-PAYLOAD), signed header = host. */
    override fun presignPut(key: String, contentType: String): String {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val region = cfg.s3Region.ifBlank { "auto" }
        val scope = "$dateStamp/$region/s3/aws4_request"
        val canonicalUri = "/${cfg.s3Bucket}/${encodePath(key)}"

        // Параметры запроса (без подписи) — должны идти отсортированными по ключу.
        val params = sortedMapOf(
            "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
            "X-Amz-Credential" to "${cfg.s3AccessKey}/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to "900",
            "X-Amz-SignedHeaders" to "host",
        )
        val canonicalQuery = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val canonicalHeaders = "host:$host\n"
        val canonicalRequest = listOf(
            "PUT",
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            "host",
            "UNSIGNED-PAYLOAD",
        ).joinToString("\n")

        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest),
        ).joinToString("\n")

        val signingKey = hmac(
            hmac(hmac(hmac(("AWS4" + cfg.s3SecretKey).toByteArray(Charsets.UTF_8), dateStamp), region), "s3"),
            "aws4_request",
        )
        val signature = hmac(signingKey, stringToSign).toHex()

        return "$endpoint$canonicalUri?$canonicalQuery&X-Amz-Signature=$signature"
    }

    // --- helpers ---

    // RFC3986-кодирование одного значения (как требует SigV4).
    private fun enc(v: String): String =
        URLEncoder.encode(v, Charsets.UTF_8)
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    // Кодируем каждый сегмент пути, разделители '/' сохраняем.
    private fun encodePath(key: String): String =
        key.split("/").joinToString("/") { enc(it) }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
