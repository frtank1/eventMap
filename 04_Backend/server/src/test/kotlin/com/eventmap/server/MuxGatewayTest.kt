package com.eventmap.server

import com.eventmap.server.data.video.MuxGatewayImpl
import com.eventmap.server.data.video.verifyMuxSignature
import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.repository.MediaFailure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class MuxGatewayTest {
    @Test fun verifiesExactBytesAndRejectsTampering() {
        val bytes = muxReady("evt-1", "mux-media")
        val timestamp = 1_800_000_000L
        val header = muxSignature(bytes, timestamp)
        assertTrue(verifyMuxSignature(bytes, header, "test-webhook-secret", timestamp))
        assertFalse(verifyMuxSignature(bytes + byteArrayOf(32), header, "test-webhook-secret", timestamp))
        assertFalse(verifyMuxSignature(bytes, header, "wrong-secret", timestamp))
        assertFalse(verifyMuxSignature(bytes, null, "test-webhook-secret", timestamp))
    }

    @Test fun rejectsExpiredFutureAndAmbiguousTimestamps() {
        val bytes = muxReady("evt-1", "mux-media")
        val timestamp = 1_800_000_000L
        val header = muxSignature(bytes, timestamp)
        assertFalse(verifyMuxSignature(bytes, header, "test-webhook-secret", timestamp + 301))
        assertFalse(verifyMuxSignature(bytes, header, "test-webhook-secret", timestamp - 301))
        assertFalse(verifyMuxSignature(bytes, "$header,t=$timestamp", "test-webhook-secret", timestamp))
        assertTrue(verifyMuxSignature(bytes, "$header,v1=bad", "test-webhook-secret", timestamp))
    }

    @Test fun parserOnlyUsesSignedPlaybackIds() {
        val gateway = MuxGatewayImpl(muxTestConfig())
        val signed = muxReady("evt-signed", "mux-media")
        assertEquals("playback-1", gateway.verifyWebhook(signed, muxSignature(signed)).playbackId)
        val public = muxReady("evt-public", "mux-media", policy = "public")
        assertNull(gateway.verifyWebhook(public, muxSignature(public)).playbackId)
        assertEquals(401, assertFailsWith<MediaFailure> { gateway.verifyWebhook(signed, "bad") }.status)
    }

    @Test fun playbackSignsVideoAndImageSeparatelyAndNeverExposesProcessingAsset() {
        val gateway = MuxGatewayImpl(muxTestConfig(), nowSeconds = { 1_800_000_000L })
        val original = Media("m", "u", "", kind = "video", provider = "mux", playbackId = "playback-1")
        val playable = gateway.playback(original)
        for ((url, aud) in listOf(playable.url to "v", playable.thumbnailUrl!! to "t")) {
            val jwt = url.substringAfter("token=").split('.')
            val verify = Signature.getInstance("SHA256withRSA").apply {
                initVerify(muxTestKeys.public); update("${jwt[0]}.${jwt[1]}".toByteArray())
            }
            assertTrue(verify.verify(Base64.getUrlDecoder().decode(jwt[2])))
            val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt[1]))).jsonObject
            assertEquals(aud, claims["aud"]!!.jsonPrimitive.content)
            assertEquals("playback-1", claims["sub"]!!.jsonPrimitive.content)
            assertEquals(1_800_003_600L, claims["exp"]!!.jsonPrimitive.long)
        }
        assertEquals("", gateway.playback(original.copy(status = "processing")).url)
        val unavailable = MuxGatewayImpl(muxTestConfig().copy(muxSigningPrivateKey = "")).playback(original)
        assertEquals("unavailable", unavailable.status)
        assertEquals("", unavailable.url)
    }

    @Test fun noConfigurationFailsClosed() = runTest {
        val gateway = MuxGatewayImpl(muxTestConfig().copy(muxTokenSecret = ""))
        assertFalse(gateway.configured)
        assertEquals(503, assertFailsWith<MediaFailure> { gateway.createUpload("m") }.status)
    }

    @Test fun realHttpClientAgainstPostmanSimulation() = runTest {
        val endpoint = System.getenv("MUX_SIMULATION_URL")
        org.junit.jupiter.api.Assumptions.assumeTrue(!endpoint.isNullOrBlank(), "Run with local Postman Mux simulation")
        val gateway = MuxGatewayImpl(muxTestConfig(), endpoint!!)
        assertEquals("sim-upload-id", gateway.createUpload("mux-test").id)
        assertEquals(502, assertFailsWith<MediaFailure> { gateway.createUpload("simulate-503") }.status)
        assertEquals(502, assertFailsWith<MediaFailure> { gateway.createUpload("simulate-html") }.status)
        val badAuth = MuxGatewayImpl(muxTestConfig().copy(muxTokenSecret = "wrong"), endpoint)
        assertEquals(502, assertFailsWith<MediaFailure> { badAuth.createUpload("mux-test").id }.status)
    }
}
