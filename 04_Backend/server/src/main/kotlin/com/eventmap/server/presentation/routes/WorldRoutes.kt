package com.eventmap.server.presentation.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Public, immutable DATA only. No user content, file-system paths, scripts or auth. */
fun Route.worldRoutes() {
    get("/world/manifest.json") { call.worldResource("manifest.json", immutable=false) }
    get("/world/places-manifest.json") { call.worldResource("places-manifest.json", immutable=false) }
    get("/world/assets/{file}") {
        val file=call.parameters["file"].orEmpty()
        if(!Regex("[a-f0-9]{64}\\.json").matches(file)) { call.respond(HttpStatusCode.NotFound);return@get }
        call.worldResource("assets/$file", immutable=true)
    }
}

private suspend fun ApplicationCall.worldResource(path:String,immutable:Boolean) {
    response.header(HttpHeaders.AccessControlAllowOrigin,"*") // public models only, including WKWebView's null origin
    response.header("X-Content-Type-Options","nosniff")
    response.header(HttpHeaders.CacheControl,if(immutable) "public, max-age=31536000, immutable" else "no-cache")
    val bytes=object {}.javaClass.classLoader.getResourceAsStream("world/$path")?.use { it.readBytes() }
    if(bytes==null) { respond(HttpStatusCode.NotFound);return }
    respondBytes(bytes,ContentType.Application.Json)
}
