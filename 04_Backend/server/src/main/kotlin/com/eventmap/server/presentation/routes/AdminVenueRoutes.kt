package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.repository.*
import com.eventmap.server.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable private data class SaveVenueRequest(val expectedVersion: Long, val building: VenueBuilding)
@Serializable private data class PublishVenueRequest(val expectedVersion: Long)
private val venueAdminJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private const val PLAN_BODY_LIMIT = 1024 * 1024

private suspend inline fun <reified T> ApplicationCall.planResponse(value: T) =
    respondText(venueAdminJson.encodeToString(value),ContentType.Application.Json)

internal fun ApplicationCall.validAdminKey(key: String): Boolean {
    val supplied=request.header("X-Admin-Key") ?: return false
    return key.isNotBlank() && supplied.length<=4096 && MessageDigest.isEqual(key.toByteArray(),supplied.toByteArray())
}

private suspend fun ApplicationCall.planAction(key: String, action: suspend ApplicationCall.() -> Unit) {
    response.header(HttpHeaders.CacheControl,"no-store")
    response.header("X-Content-Type-Options","nosniff")
    if(!validAdminKey(key)) {
        respond(HttpStatusCode.Forbidden,ErrorResponse(ErrorResponse.Body("FORBIDDEN","Неверный админ-ключ")));return
    }
    try { action() } catch(e: VenueEditError) {
        val status=when(e.code) {
            "NOT_FOUND" -> HttpStatusCode.NotFound
            "VERSION_CONFLICT", "REFERENCES_REMOVED" -> HttpStatusCode.Conflict
            "BODY_TOO_LARGE" -> HttpStatusCode.PayloadTooLarge
            "REQUEST_TIMEOUT" -> HttpStatusCode.RequestTimeout
            else -> HttpStatusCode.BadRequest
        }
        respond(status,ErrorResponse(ErrorResponse.Body(e.code,e.message)))
    }
}
private fun ApplicationCall.venueId(): String = parameters["buildingId"].orEmpty().also {
    if(!validBuildingId(it))throw VenueEditError("INVALID_BUILDING","Некорректный ID здания")
}
private suspend inline fun <reified T> ApplicationCall.planBody(): T {
    if(request.contentType().withoutParameters()!=ContentType.Application.Json)
        throw VenueEditError("INVALID_JSON","Ожидается application/json")
    if((request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0)>PLAN_BODY_LIMIT)
        throw VenueEditError("BODY_TOO_LARGE","План превышает 1 MiB")
    val bytes=try { withTimeout(15_000) { receiveChannel().readRemaining((PLAN_BODY_LIMIT+1).toLong()).readBytes() } }
        catch(e: kotlinx.coroutines.TimeoutCancellationException) { throw VenueEditError("REQUEST_TIMEOUT","Не удалось получить тело запроса за 15 секунд") }
    if(bytes.size>PLAN_BODY_LIMIT)throw VenueEditError("BODY_TOO_LARGE","План превышает 1 MiB")
    return try { venueAdminJson.decodeFromString<T>(bytes.decodeToString(throwOnInvalidSequence=true)) }
    catch(e: SerializationException) { throw VenueEditError("INVALID_JSON","Некорректный JSON или неподдерживаемые поля") }
    catch(e: CharacterCodingException) { throw VenueEditError("INVALID_JSON","Требуется UTF-8") }
}

fun Route.adminVenueRoutes(editor: VenueEditor, adminKey: String) {
    // Fixed resources only; never resolve a client-supplied filesystem path.
    for((url,file,type) in listOf(
        Triple("/admin","index.html",ContentType.Text.Html),
        Triple("/admin/venues","index.html",ContentType.Text.Html),
        Triple("/admin/venues.js","editor.js",ContentType.Application.JavaScript),
        Triple("/admin/console.js","console.js",ContentType.Application.JavaScript),
        Triple("/admin/venues.css","editor.css",ContentType.Text.CSS),
    ))get(url) {
        call.response.header(HttpHeaders.CacheControl,"no-store")
        call.response.header("X-Content-Type-Options","nosniff")
        call.response.header("Referrer-Policy","no-referrer")
        call.response.header("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' blob:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
        val content=VenueEditor::class.java.classLoader.getResourceAsStream("admin-venues/$file")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        call.respondText(content,type)
    }
    get("/admin/api/venues") { call.planAction(adminKey) { planResponse(editor.list()) } }
    get("/admin/api/venues/{buildingId}") { call.planAction(adminKey) {
        planResponse(editor.editor(venueId()) ?: throw VenueEditError("NOT_FOUND","Здание не найдено"))
    } }
    put("/admin/api/venues/{buildingId}/draft") { call.planAction(adminKey) {
        val id=venueId();val body=planBody<SaveVenueRequest>()
        planResponse(editor.save(id,body.expectedVersion,body.building))
    } }
    post("/admin/api/venues/{buildingId}/publications") { call.planAction(adminKey) {
        val id=venueId();val body=planBody<PublishVenueRequest>()
        planResponse(editor.publish(id,body.expectedVersion))
    } }
    get("/admin/api/venues/{buildingId}/revisions/{revision}") { call.planAction(adminKey) {
        val id=venueId();val revision=parameters["revision"].orEmpty()
        if(!Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}").matches(revision))throw VenueEditError("INVALID_REVISION","Некорректная ревизия")
        planResponse(editor.revision(id,revision) ?: throw VenueEditError("NOT_FOUND","Версия не найдена"))
    } }
}
