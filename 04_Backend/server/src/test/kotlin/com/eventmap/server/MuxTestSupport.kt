package com.eventmap.server

import com.eventmap.server.config.AppConfig
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal val muxTestKeys: KeyPair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
internal fun muxTestConfig() = AppConfig(appEnv = "dev", videoProvider = "mux", muxTokenId = "test-id",
    muxTokenSecret = "test-secret", muxWebhookSecret = "test-webhook-secret", muxSigningKeyId = "test-key",
    muxSigningPrivateKey = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(muxTestKeys.private.encoded)}\n-----END PRIVATE KEY-----")

internal fun muxSignature(body: ByteArray, timestamp: Long = System.currentTimeMillis() / 1000): String {
    val mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec("test-webhook-secret".toByteArray(), "HmacSHA256"))
        update("$timestamp.".toByteArray())
    }.doFinal(body).joinToString("") { "%02x".format(it) }
    return "t=$timestamp,v1=$mac"
}

// Shape follows the provider's video.asset.ready example, with synthetic IDs.
internal fun muxReady(id: String, mediaId: String, duration: Double = 18.0, policy: String = "signed") = """
    {"type":"video.asset.ready","object":{"type":"asset","id":"asset-1"},"id":"$id",
     "data":{"id":"asset-1","upload_id":"upload-1","passthrough":"$mediaId","status":"ready",
       "duration":$duration,"playback_ids":[{"id":"playback-1","policy":"$policy"}],
       "tracks":[{"type":"video","duration":$duration,"max_width":1080,"max_height":1920}]}}
""".trimIndent().toByteArray()
