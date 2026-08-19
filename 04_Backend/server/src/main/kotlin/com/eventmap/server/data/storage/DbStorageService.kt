package com.eventmap.server.data.storage

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.db.MediaBlobsTable
import com.eventmap.server.domain.repository.MediaBlobRepository
import com.eventmap.server.domain.repository.StoragePort
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// =====================================================================
//  Фолбэк-хостинг фото/видео в Postgres (V10) — включается автоматически,
//  когда S3_*/R2 не заданы (см. AppModule). Тот же контракт presign→PUT→confirm:
//   - uploadUrl → PUT {publicBase}/media/upload/{key}?exp&sig (HMAC, 15 мин)
//   - publicUrl → GET {publicBase}/media/files/{key}
//  Лимиты: image ≤ 8 МБ, video ≤ 64 МБ (см. MediaRoutes). Free-Postgres 1 ГБ —
//  для теста/демо; прод — R2 (05_Docs/STORAGE_SETUP.md).
// =====================================================================

class DbStorageService(private val cfg: AppConfig) : StoragePort {

    override val configured: Boolean = true // фолбэк доступен всегда (нужна только БД)

    private val base: String get() = cfg.publicBaseUrl.trimEnd('/')

    override fun presignPut(key: String, contentType: String): String {
        val exp = System.currentTimeMillis() + UPLOAD_TTL_MS
        val sig = StorageTokens.sign(key, exp, cfg.adminKey)
        return "$base/media/upload/${encodePath(key)}?exp=$exp&sig=$sig"
    }

    override fun publicUrl(key: String): String = "$base/media/files/${encodePath(key)}"

    private fun encodePath(key: String): String =
        key.split("/").joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20")
        }

    companion object {
        const val UPLOAD_TTL_MS = 15 * 60 * 1000L
    }
}

/** Подпись ссылок загрузки: HMAC-SHA256(key|exp, ADMIN_KEY) — bearer на PUT не нужен. */
object StorageTokens {
    fun sign(key: String, expMs: Long, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$key|$expMs".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun verify(key: String, expMs: Long, sig: String, secret: String): Boolean =
        expMs > System.currentTimeMillis() &&
            java.security.MessageDigest.isEqual( // constant-time: не палим подпись по таймингу
                sign(key, expMs, secret).toByteArray(Charsets.UTF_8),
                sig.lowercase().toByteArray(Charsets.UTF_8),
            )
}

class MediaBlobRepositoryImpl : MediaBlobRepository {

    override suspend fun save(key: String, contentType: String, bytes: ByteArray): Unit =
        newSuspendedTransaction(Dispatchers.IO) {
            MediaBlobsTable.upsert { // одна операция — без гонки update→insert
                it[MediaBlobsTable.key] = key
                it[MediaBlobsTable.contentType] = contentType
                it[MediaBlobsTable.bytes] = bytes
                it[size] = bytes.size
                it[createdAtMs] = System.currentTimeMillis()
            }
        }

    override suspend fun get(key: String): Pair<String, ByteArray>? =
        newSuspendedTransaction(Dispatchers.IO) {
            MediaBlobsTable.selectAll().where { MediaBlobsTable.key eq key }
                .singleOrNull()
                ?.let { it[MediaBlobsTable.contentType] to it[MediaBlobsTable.bytes] }
        }
}
