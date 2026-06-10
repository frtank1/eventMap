package com.eventmap.server

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.db.DatabaseFactory
import com.eventmap.server.di.appModule
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.routes.authRoutes
import com.eventmap.server.presentation.routes.eventRoutes
import com.eventmap.server.presentation.routes.organizerRoutes
import com.eventmap.server.security.JwtService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = AppConfig().port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    val config: AppConfig by inject()
    DatabaseFactory.init(config)

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; prettyPrint = true }) }
    install(CallLogging)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ErrorResponse.Body("INTERNAL", cause.message ?: "error")))
        }
    }

    val jwt: JwtService by inject()
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "eventmap"
            verifier(jwt.verifier)
            validate { cred ->
                if (cred.payload.getClaim("userId").asString() != null) JWTPrincipal(cred.payload) else null
            }
        }
    }

    val getEvents: GetEventsUseCase by inject()
    val getEvent: GetEventUseCase by inject()
    val getOrganizer: GetOrganizerUseCase by inject()
    val getOrganizerEvents: GetOrganizerEventsUseCase by inject()
    val register: RegisterUseCase by inject()
    val login: LoginUseCase by inject()
    val issueTokens: IssueTokensUseCase by inject()
    val refreshSession: RefreshSessionUseCase by inject()
    val logout: LogoutUseCase by inject()

    routing {
        authRoutes(register, login, issueTokens, refreshSession, logout)
        eventRoutes(getEvents, getEvent)
        organizerRoutes(getOrganizer, getOrganizerEvents)
    }
}
