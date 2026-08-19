package com.eventmap.server.presentation.routes

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.storage.StorageTokens
import com.eventmap.server.domain.repository.MediaBlobRepository
import com.eventmap.server.domain.usecase.ConfirmMediaUseCase
import com.eventmap.server.domain.usecase.GetEventMediaUseCase
import com.eventmap.server.domain.usecase.PresignUploadUseCase
import com.eventmap.server.presentation.dto.ConfirmMediaRequest
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.dto.PresignRequest
import com.eventmap.server.presentation.dto.PresignResponse
import com.eventmap.server.presentation.dto.toDto
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

private fun ApplicationCall.uid(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

// Лимиты фолбэк-хранилища (клиент режет так же: 8 МБ обложка, 60 МБ ролик)
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
private const val MAX_VIDEO_BYTES = 64 * 1024 * 1024

// Зеркало whitelist'а presign'а (MediaUseCases: IMAGE_TYPES/VIDEO_TYPES)
private val ALLOWED_BLOB_TYPES = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic",
    "video/mp4", "video/quicktime", "video/webm",
)

// Медиа: presigned-загрузка в R2/S3 + список медиа события (§8 storage).
// Без S3_* работает фолбэк: PUT /media/upload/{key} и GET /media/files/{key} (V10).
fun Route.mediaRoutes(
    presign: PresignUploadUseCase,
    confirm: ConfirmMediaUseCase,
    eventMedia: GetEventMediaUseCase,
    blobs: MediaBlobRepository,
    config: AppConfig,
) {
    // Публично: медиа события (гостям тоже)
    get("/events/{id}/media") {
        call.respond(eventMedia(call.parameters["id"].orEmpty()).map { it.toDto() })
    }

    authenticate("auth-jwt") {
        post("/media/presign") {
            val uid = call.uid() ?: return@post call.respond(
                HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")),
            )
            val body = call.receive<PresignRequest>()
            when (val r = presign(uid, body.kind, body.contentType)) {
                is PresignUploadUseCase.Result.Ok ->
                    call.respond(PresignResponse(r.mediaId, r.uploadUrl, r.publicUrl))
                is PresignUploadUseCase.Result.NotConfigured ->
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(ErrorResponse.Body("STORAGE_OFF", "Хранилище не настроено (S3_* env)")))
                is PresignUploadUseCase.Result.Rejected ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body(r.code, r.message)))
            }
        }

        post("/media/confirm") {
            val uid = call.uid() ?: return@post call.respond(
                HttpStatusCode.Unauthorized, ErrorResponse(ErrorResponse.Body("UNAUTHORIZED", "Нужен токен")),
            )
            val body = call.receive<ConfirmMediaRequest>()
            val media = confirm(body.mediaId, uid, body.url, body.contentType, body.kind, body.eventId)
            call.respond(HttpStatusCode.Created, media.toDto())
        }
    }

    // ── Фолбэк-хранилище (V10, без S3_*) ─────────────────────────────────
    // PUT байтов по подписанной ссылке из presign. Bearer не нужен — защищает HMAC+TTL.
    put("/media/upload/{key...}") {
        val key = call.parameters.getAll("key")?.joinToString("/").orEmpty()
        val exp = call.request.queryParameters["exp"]?.toLongOrNull() ?: 0L
        val sig = call.request.queryParameters["sig"].orEmpty()
        if (key.isBlank() || !StorageTokens.verify(key, exp, sig, config.adminKey)) {
            return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse(ErrorResponse.Body("BAD_SIGNATURE", "Ссылка загрузки недействительна или истекла")))
        }
        val limit = if (key.startsWith("video/")) MAX_VIDEO_BYTES else MAX_IMAGE_BYTES
        val declared = call.request.header("Content-Length")?.toLongOrNull()
        if (declared != null && declared > limit) {
            return@put call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(ErrorResponse.Body("TOO_LARGE", "Файл больше ${limit / 1024 / 1024} МБ")))
        }
        val bytes = call.receive<ByteArray>()
        if (bytes.size > limit) {
            return@put call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(ErrorResponse.Body("TOO_LARGE", "Файл больше ${limit / 1024 / 1024} МБ")))
        }
        // Храним только типы из whitelist presign'а — чужой content-type (html и т.п.) не отдаём
        val contentType = call.request.contentType().let { "${it.contentType}/${it.contentSubtype}" }.lowercase()
        if (contentType !in ALLOWED_BLOB_TYPES) {
            return@put call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(ErrorResponse.Body("BAD_TYPE", "Неподдерживаемый content-type")))
        }
        blobs.save(key, contentType, bytes)
        call.respond(HttpStatusCode.OK)
    }

    // Отдача файла (публично): агрессивный кэш — key иммутабелен (uuid в имени).
    // HTTP Range (bytes=) — для перемотки в видеоплеерах (ExoPlayer/AVPlayer).
    get("/media/files/{key...}") {
        val key = call.parameters.getAll("key")?.joinToString("/").orEmpty()
        val blob = blobs.get(key)
        if (blob == null) {
            return@get call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorResponse.Body("NOT_FOUND", "Файл не найден")))
        }
        val (type, bytes) = blob
        call.response.header("Cache-Control", "public, max-age=31536000, immutable")
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Accept-Ranges", "bytes")

        val range = parseByteRange(call.request.header("Range"), bytes.size)
        if (range == null) {
            call.respondBytes(bytes, ContentType.parse(type))
        } else {
            val (from, to) = range
            call.response.header("Content-Range", "bytes $from-$to/${bytes.size}")
            call.respondBytes(bytes.copyOfRange(from, to + 1), ContentType.parse(type), HttpStatusCode.PartialContent)
        }
    }
}

/** "bytes=a-b" → a..b (включительно) в пределах size; null — без Range/невалидный (отдаём целиком). */
private fun parseByteRange(header: String?, size: Int): Pair<Int, Int>? {
    if (header == null || !header.startsWith("bytes=") || "," in header) return null
    val spec = header.removePrefix("bytes=").trim()
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val fromRaw = spec.take(dash).trim()
    val toRaw = spec.substring(dash + 1).trim()
    return when {
        fromRaw.isNotEmpty() -> {
            val from = fromRaw.toIntOrNull() ?: return null
            if (from >= size || from < 0) return null
            val to = toRaw.toIntOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
            if (to < from) return null
            from to to
        }
        toRaw.isNotEmpty() -> { // суффикс: последние N байт
            val n = toRaw.toIntOrNull() ?: return null
            if (n <= 0) return null
            (size - n).coerceAtLeast(0) to (size - 1)
        }
        else -> null
    }
}
