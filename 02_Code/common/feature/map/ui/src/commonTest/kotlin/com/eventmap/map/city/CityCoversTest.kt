package com.eventmap.map.city

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.*

class CityCoversTest {
    private fun event(id: String = "e", ref: String = "cover") = EventDetail(id, "Event", cover = ref)
    private fun photo(id: String = "cover") = MediaItem(id, "https://cdn.example.invalid/$id.jpg")
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())

    @Test fun matchesCoverIdNotFirstPhotoAndReusesBoundedCache() = runTest {
        var reads = 0; var downloads = 0
        val client = HttpClient(MockEngine { request ->
            downloads++; assertEquals("/cover.jpg", request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        })
        try {
            val loader = CityCovers(client, { reads++; KtorResponse.Success(listOf(photo("wrong"), photo())) }, { jpeg })
            repeat(2) {
                val result = loader.load(listOf("e"), listOf(event()))
                assertEquals("cover", result.single().ref); assertTrue(result.single().data.startsWith("data:image/jpeg;base64,"))
            }
            assertEquals(1, reads); assertEquals(1, downloads)
        } finally { client.close() }
    }

    @Test fun privateDeletedAndReplacedCoversNeverReuseOldImage() = runTest {
        val client = HttpClient(MockEngine { respond(jpeg, headers = headersOf(HttpHeaders.ContentType, "image/jpeg")) })
        try {
            var read = 0
            val loader = CityCovers(client, { read++; KtorResponse.Success(listOf(photo())) }, { jpeg })
            assertEquals(1, loader.load(listOf("e"), listOf(event())).size)
            assertTrue(loader.load(listOf("e"), listOf(event(ref="new-cover"))).isEmpty())
            assertTrue(loader.load(listOf("e"), listOf(event().copy(visibility="private"))).isEmpty())
            assertTrue(loader.load(listOf("e"), emptyList()).isEmpty())
            assertEquals(2, read)
        } finally { client.close() }
    }

    @Test fun badImagesAreNegativeCachedAndRequestsAreLimited() = runTest {
        var active = 0; var peak = 0; var count = 0; var decoded = 0
        val counterLock = Mutex() // MockEngine handlers may run on different worker threads.
        val client = HttpClient(MockEngine {
            counterLock.withLock { active++; count++; peak = maxOf(peak,active) }
            delay(20)
            counterLock.withLock { active-- }
            respond("not an image", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val loader = CityCovers(client, { KtorResponse.Success(listOf(photo())) }, { decoded++; jpeg })
            val events = (1..20).map { event("e$it") }
            repeat(2) { assertTrue(loader.load(events.map { it.id }, events).isEmpty()) }
            assertEquals(8,count); assertTrue(peak<=2,"concurrent downloads: $peak"); assertEquals(0,decoded)
        } finally { client.close() }
    }

    @Test fun rejectsRedirectsLargeResponsesAndUnsafeUrls() = runTest {
        for (bad in listOf("http://example.invalid/a.jpg", "file:///a", "data:image/png;base64,AA", "https://user:pass@example.invalid/a")) assertFalse(coverUrl(bad))
        assertTrue(coverUrl("https://cdn.example.invalid/a.jpg"))
        for (status in listOf(HttpStatusCode.Found,HttpStatusCode.OK)) {
            val client = HttpClient(MockEngine { respond(jpeg,status,headersOf(HttpHeaders.ContentType to listOf("image/jpeg"),HttpHeaders.ContentLength to listOf("9000000"))) }) { followRedirects=false }
            try {
                val loader = CityCovers(client,{ KtorResponse.Success(listOf(photo())) },{ fail("must not decode rejected body") })
                assertTrue(loader.load(listOf("e"),listOf(event())).isEmpty())
            } finally { client.close() }
        }
    }

    @Test fun cancellationIsNotSwallowedOrCached() = runTest {
        val client = HttpClient(MockEngine { error("not reached") })
        try {
            val loader = CityCovers(client,{ throw CancellationException("screen closed") },{ jpeg })
            assertFailsWith<CancellationException> { loader.load(listOf("e"),listOf(event())) }
        } finally { client.close() }
    }
}
