package com.eventmap.server.presentation.routes

import com.eventmap.server.domain.repository.MediaFailure
import com.eventmap.server.domain.usecase.MuxVideoService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.core.readBytes

fun Route.muxRoutes(mux: MuxVideoService) {
    post("/webhooks/mux") {
        call.mediaResult {
            // Bound memory even for chunked requests; never log the body or signature.
            val bytes = call.receiveChannel().readRemaining(256 * 1024L + 1).readBytes()
            if (bytes.size > 256 * 1024) throw MediaFailure(413, "TOO_LARGE", "Webhook слишком большой")
            mux.webhook(bytes, call.request.header("mux-signature"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
