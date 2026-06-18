package com.eventmap.server

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.db.DatabaseFactory
import com.eventmap.server.di.appModule
import com.eventmap.server.domain.usecase.AddCommentUseCase
import com.eventmap.server.domain.usecase.AddPinAnimationUseCase
import com.eventmap.server.domain.usecase.ApplyBusinessUseCase
import com.eventmap.server.domain.usecase.AttendUseCase
import com.eventmap.server.domain.usecase.CheckinQuestPointUseCase
import com.eventmap.server.domain.usecase.CreateQuestUseCase
import com.eventmap.server.domain.usecase.GetMyBusinessUseCase
import com.eventmap.server.domain.usecase.GetPinAnimationsUseCase
import com.eventmap.server.domain.usecase.GetQuestUseCase
import com.eventmap.server.domain.usecase.GetQuestsNearbyUseCase
import com.eventmap.server.domain.usecase.CreateCapsuleUseCase
import com.eventmap.server.domain.usecase.CreateInvitationUseCase
import com.eventmap.server.domain.usecase.GetCapsulesNearbyUseCase
import com.eventmap.server.domain.usecase.GetDistrictLeaderboardUseCase
import com.eventmap.server.domain.usecase.GetFogUseCase
import com.eventmap.server.domain.usecase.GetGameProfileUseCase
import com.eventmap.server.domain.usecase.GetMissionsUseCase
import com.eventmap.server.domain.usecase.OpenCapsuleUseCase
import com.eventmap.server.domain.usecase.GetFriendsUseCase
import com.eventmap.server.domain.usecase.GetInvitationByTokenUseCase
import com.eventmap.server.domain.usecase.GetInvitationForGuestUseCase
import com.eventmap.server.domain.usecase.GetReceiptCodeUseCase
import com.eventmap.server.domain.usecase.GetRsvpDashboardUseCase
import com.eventmap.server.domain.usecase.RespondFriendRequestUseCase
import com.eventmap.server.domain.usecase.ReviewQuestUseCase
import com.eventmap.server.domain.usecase.RsvpUseCase
import com.eventmap.server.domain.usecase.SendFriendRequestUseCase
import com.eventmap.server.domain.usecase.ReviewBusinessUseCase
import com.eventmap.server.domain.usecase.UpdateBusinessPinUseCase
import com.eventmap.server.domain.usecase.CheckinTicketUseCase
import com.eventmap.server.domain.usecase.CreateEventUseCase
import com.eventmap.server.domain.usecase.GetMyTicketsUseCase
import com.eventmap.server.domain.usecase.GetTicketQrUseCase
import com.eventmap.server.domain.usecase.PurchaseTicketsUseCase
import com.eventmap.server.domain.usecase.GetCommentsUseCase
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.ToggleCommentLikeUseCase
import com.eventmap.server.domain.usecase.ToggleReminderUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.routes.authRoutes
import com.eventmap.server.presentation.routes.businessRoutes
import com.eventmap.server.presentation.routes.eventRoutes
import com.eventmap.server.presentation.routes.friendRoutes
import com.eventmap.server.data.pulse.PulseService
import com.eventmap.server.presentation.routes.gameRoutes
import com.eventmap.server.presentation.routes.mapRoutes
import com.eventmap.server.presentation.routes.organizerRoutes
import com.eventmap.server.presentation.routes.questRoutes
import com.eventmap.server.presentation.routes.socialRoutes
import com.eventmap.server.presentation.routes.ticketRoutes
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // dev: CORS_HOSTS пуст → anyHost; prod: только whitelist (проверка в AppConfig)
        if (config.corsHosts.isEmpty()) {
            anyHost()
        } else {
            config.corsHosts.forEach { allowHost(it, schemes = listOf("https")) }
        }
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
    val createEvent: CreateEventUseCase by inject()
    val getOrganizer: GetOrganizerUseCase by inject()
    val getOrganizerEvents: GetOrganizerEventsUseCase by inject()
    val register: RegisterUseCase by inject()
    val login: LoginUseCase by inject()
    val issueTokens: IssueTokensUseCase by inject()
    val refreshSession: RefreshSessionUseCase by inject()
    val logout: LogoutUseCase by inject()
    val getComments: GetCommentsUseCase by inject()
    val addComment: AddCommentUseCase by inject()
    val toggleLike: ToggleCommentLikeUseCase by inject()
    val toggleReminder: ToggleReminderUseCase by inject()
    val attend: AttendUseCase by inject()
    val purchase: PurchaseTicketsUseCase by inject()
    val myTickets: GetMyTicketsUseCase by inject()
    val ticketQr: GetTicketQrUseCase by inject()
    val checkinTicket: CheckinTicketUseCase by inject()
    val applyBusiness: ApplyBusinessUseCase by inject()
    val myBusiness: GetMyBusinessUseCase by inject()
    val pinAnimations: GetPinAnimationsUseCase by inject()
    val updateBusinessPin: UpdateBusinessPinUseCase by inject()
    val reviewBusiness: ReviewBusinessUseCase by inject()
    val addAnimation: AddPinAnimationUseCase by inject()
    val createQuest: CreateQuestUseCase by inject()
    val questsNearby: GetQuestsNearbyUseCase by inject()
    val getQuest: GetQuestUseCase by inject()
    val questCheckin: CheckinQuestPointUseCase by inject()
    val receiptCode: GetReceiptCodeUseCase by inject()
    val reviewQuest: ReviewQuestUseCase by inject()
    val sendFriendRequest: SendFriendRequestUseCase by inject()
    val respondFriend: RespondFriendRequestUseCase by inject()
    val getFriends: GetFriendsUseCase by inject()
    val createInvitation: CreateInvitationUseCase by inject()
    val invitationForGuest: GetInvitationForGuestUseCase by inject()
    val invitationByToken: GetInvitationByTokenUseCase by inject()
    val rsvp: RsvpUseCase by inject()
    val rsvpDashboard: GetRsvpDashboardUseCase by inject()
    val gameProfile: GetGameProfileUseCase by inject()
    val missions: GetMissionsUseCase by inject()
    val fog: GetFogUseCase by inject()
    val districtLeaderboard: GetDistrictLeaderboardUseCase by inject()
    val createCapsule: CreateCapsuleUseCase by inject()
    val capsulesNearby: GetCapsulesNearbyUseCase by inject()
    val openCapsule: OpenCapsuleUseCase by inject()
    val pulseService: PulseService by inject()
    pulseService.startJob() // агрегация пульса каждые 30 сек + TTL-чистка пингов

    routing {
        // Health-check для Render/любого хоста — должен возвращать 200 без БД и авторизации.
        get("/health") { call.respondText("OK") }
        authRoutes(register, login, issueTokens, refreshSession, logout)
        eventRoutes(getEvents, getEvent, createEvent)
        organizerRoutes(getOrganizer, getOrganizerEvents)
        socialRoutes(getComments, addComment, toggleLike, toggleReminder, attend)
        ticketRoutes(purchase, myTickets, ticketQr, checkinTicket)
        businessRoutes(applyBusiness, myBusiness, pinAnimations, updateBusinessPin, reviewBusiness, addAnimation, config.adminKey)
        questRoutes(createQuest, questsNearby, getQuest, questCheckin, receiptCode, reviewQuest, config.adminKey)
        friendRoutes(sendFriendRequest, respondFriend, getFriends, createInvitation, invitationForGuest, invitationByToken, rsvp, rsvpDashboard)
        gameRoutes(gameProfile, missions, fog, districtLeaderboard, createCapsule, capsulesNearby, openCapsule)
        mapRoutes(pulseService)
    }
}
