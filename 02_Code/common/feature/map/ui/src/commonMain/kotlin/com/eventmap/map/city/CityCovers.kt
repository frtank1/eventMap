package com.eventmap.map.city

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.readByteArray
import kotlin.io.encoding.Base64
import kotlin.time.Clock

internal expect fun cityCoverThumbnail(bytes: ByteArray): ByteArray?

internal fun coverUrl(value: String): Boolean = runCatching {
    val u = Url(value)
    u.protocol == URLProtocol.HTTPS && u.host.isNotBlank() && u.user == null && u.password == null
}.getOrDefault(false)

/** Small in-memory, screen-scoped cache. Never persists private data or account tokens. */
internal class CityCovers(
    private val client: HttpClient,
    private val media: suspend (String) -> KtorResponse<List<MediaItem>>,
    private val thumbnail: suspend (ByteArray) -> ByteArray? = { withContext(Dispatchers.Default) { cityCoverThumbnail(it) } },
) {
    private data class Key(val event: String, val ref: String)
    private data class Cached(val data: String?, val at: Long)
    private val cache = linkedMapOf<Key, Cached>()
    private val slots = Semaphore(2)

    suspend fun load(ids: List<String>, events: List<EventDetail>): List<CityCover> = coroutineScope {
        val allowed = events.filter { it.visibility == "public" && it.status !in setOf("ended", "cancelled") }
            .filter { (it.expiresAtMs ?: Long.MAX_VALUE) > Clock.System.now().toEpochMilliseconds() }
            .take(150).associateBy { it.id }
        cache.keys.retainAll(allowed.values.mapNotNull { e -> e.cover?.let { Key(e.id, it) } }.toSet())
        ids.distinct().take(8).mapNotNull { allowed[it] }.mapNotNull { e ->
            e.cover?.takeIf { it.isNotBlank() }?.let { ref -> async {
                slots.withPermit {
                    val key = Key(e.id, ref)
                    val now = Clock.System.now().toEpochMilliseconds()
                    var entry = cache.remove(key)
                    if (entry == null || (entry.data == null && now - entry.at > 60_000)) {
                        val data = try {
                            // Do not substitute the first photo/reel if the cover is absent.
                            val item = (media(e.id) as? KtorResponse.Success)?.data
                                ?.firstOrNull { it.id == ref && it.type == "photo" && it.status == "ready" }
                            item?.url?.takeIf(::coverUrl)?.let { url ->
                                client.prepareGet(url).execute { response ->
                                    val limit = 4 * 1024 * 1024
                                    if (response.status != HttpStatusCode.OK ||
                                        response.contentType()?.withoutParameters()?.toString() !in setOf("image/jpeg", "image/png", "image/webp") ||
                                        (response.contentLength() ?: 0) > limit) return@execute null
                                    val bytes = response.bodyAsChannel().readRemaining((limit + 1).toLong()).readByteArray()
                                    if (bytes.isEmpty() || bytes.size > limit) return@execute null
                                    thumbnail(bytes)?.takeIf { it.size in 3..32768 && it[0] == 0xff.toByte() && it[1] == 0xd8.toByte() }
                                        ?.let { "data:image/jpeg;base64," + Base64.Default.encode(it) }
                                }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                        entry = Cached(data, now)
                    }
                    cache[key] = entry
                    while (cache.size > 24) cache.remove(cache.keys.first())
                    entry.data?.let { CityCover(e.id, ref, it) }
                }
            } }
        }.awaitAll().filterNotNull()
    }
}
