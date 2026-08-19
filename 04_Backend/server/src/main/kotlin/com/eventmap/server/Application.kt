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
import com.eventmap.server.domain.usecase.ConfirmMediaUseCase
import com.eventmap.server.domain.usecase.CreateVideoUseCase
import com.eventmap.server.domain.usecase.GetCommentsUseCase
import com.eventmap.server.domain.usecase.GetEventMediaUseCase
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.GetVideoFeedUseCase
import com.eventmap.server.domain.usecase.PresignUploadUseCase
import com.eventmap.server.domain.usecase.ReactVideoUseCase
import com.eventmap.server.domain.usecase.RegisterPushTokenUseCase
import com.eventmap.server.domain.usecase.SendPushUseCase
import com.eventmap.server.domain.usecase.ToggleCommentLikeUseCase
import com.eventmap.server.domain.usecase.ToggleReminderUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.domain.usecase.GetMyProfileUseCase
import com.eventmap.server.domain.usecase.UpdateMyProfileUseCase
import com.eventmap.server.domain.usecase.SaveInterestsUseCase
import com.eventmap.server.domain.usecase.GetUserHistoryUseCase
import com.eventmap.server.domain.usecase.GetEventParticipantsUseCase
import com.eventmap.server.domain.usecase.GetFeedUseCase
import com.eventmap.server.domain.usecase.GetNotificationsUseCase
import com.eventmap.server.domain.usecase.MarkNotificationsReadUseCase
import com.eventmap.server.domain.usecase.GetSubscriptionsUseCase
import com.eventmap.server.domain.usecase.SubscribeOrganizerUseCase
import com.eventmap.server.domain.usecase.GetSubscriptionRequestsUseCase
import com.eventmap.server.domain.usecase.RespondSubscriptionRequestUseCase
import com.eventmap.server.domain.usecase.SearchUseCase
import com.eventmap.server.domain.usecase.RateEventUseCase
import com.eventmap.server.domain.usecase.GetEventRatingsUseCase
import com.eventmap.server.domain.usecase.GetEventTicketStatusUseCase
import com.eventmap.server.presentation.dto.ErrorResponse
import com.eventmap.server.presentation.routes.authRoutes
import com.eventmap.server.presentation.routes.adminRoutes
import com.eventmap.server.presentation.routes.businessRoutes
import com.eventmap.server.presentation.routes.eventRoutes
import com.eventmap.server.presentation.routes.friendRoutes
import com.eventmap.server.data.pulse.PulseService
import com.eventmap.server.presentation.routes.gameRoutes
import com.eventmap.server.presentation.routes.mapRoutes
import com.eventmap.server.presentation.routes.mediaRoutes
import com.eventmap.server.presentation.routes.pushRoutes
import com.eventmap.server.presentation.routes.videoRoutes
import com.eventmap.server.presentation.routes.organizerRoutes
import com.eventmap.server.presentation.routes.questRoutes
import com.eventmap.server.presentation.routes.socialRoutes
import com.eventmap.server.presentation.routes.ticketRoutes
import com.eventmap.server.presentation.routes.userRoutes
import com.eventmap.server.presentation.routes.feedRoutes
import com.eventmap.server.presentation.routes.searchRoutes
import com.eventmap.server.security.JwtService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
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
import kotlinx.coroutines.launch
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

    // Демо-сид (только dev): пустая база после первого деплоя → карта с событиями Астаны.
    // Идемпотентно — при непустой базе no-op. Прод не трогаем.
    if (config.appEnv == "dev") {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { com.eventmap.server.data.seed.DemoSeed.seedIfEmpty() }
        }
    }

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
    val presignUpload: PresignUploadUseCase by inject()
    val confirmMedia: ConfirmMediaUseCase by inject()
    val eventMedia: GetEventMediaUseCase by inject()
    val videoFeed: GetVideoFeedUseCase by inject()
    val reactVideo: ReactVideoUseCase by inject()
    val createVideo: CreateVideoUseCase by inject()
    val registerPushToken: RegisterPushTokenUseCase by inject()
    val sendPush: SendPushUseCase by inject()
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
    val shareLocation: com.eventmap.server.domain.usecase.ShareMyLocationUseCase by inject()
    val stopSharing: com.eventmap.server.domain.usecase.StopSharingLocationUseCase by inject()
    val friendLocations: com.eventmap.server.domain.usecase.GetFriendLocationsUseCase by inject()
    val waveToFriend: com.eventmap.server.domain.usecase.WaveToFriendUseCase by inject()
    val createInvitation: CreateInvitationUseCase by inject()
    val invitationForGuest: GetInvitationForGuestUseCase by inject()
    val invitationByToken: GetInvitationByTokenUseCase by inject()
    val rsvp: RsvpUseCase by inject()
    val rsvpDashboard: GetRsvpDashboardUseCase by inject()
    val gameProfile: GetGameProfileUseCase by inject()
    val missions: GetMissionsUseCase by inject()
    val fog: GetFogUseCase by inject()
    val districtLeaderboard: GetDistrictLeaderboardUseCase by inject()
    val userLeaderboard: com.eventmap.server.domain.usecase.GetUserLeaderboardUseCase by inject()
    val createCapsule: CreateCapsuleUseCase by inject()
    val capsulesNearby: GetCapsulesNearbyUseCase by inject()
    val openCapsule: OpenCapsuleUseCase by inject()
    val pulseService: PulseService by inject()
    pulseService.startJob() // агрегация пульса каждые 30 сек + TTL-чистка пингов

    // V9: профиль H1, лента/уведомления F4, подписки F1, поиск G2, оценки P4
    val getMyProfile: GetMyProfileUseCase by inject()
    val updateMyProfile: UpdateMyProfileUseCase by inject()
    val saveInterests: SaveInterestsUseCase by inject()
    val getUserHistory: GetUserHistoryUseCase by inject()
    val eventParticipants: GetEventParticipantsUseCase by inject()
    val getFeed: GetFeedUseCase by inject()
    val getNotifications: GetNotificationsUseCase by inject()
    val markNotificationsRead: MarkNotificationsReadUseCase by inject()
    val getSubscriptions: GetSubscriptionsUseCase by inject()
    val subscribeOrganizer: SubscribeOrganizerUseCase by inject()
    val getSubscriptionRequests: GetSubscriptionRequestsUseCase by inject()
    val respondSubscriptionRequest: RespondSubscriptionRequestUseCase by inject()
    val searchUseCase: SearchUseCase by inject()
    val rateEvent: RateEventUseCase by inject()
    val eventRatings: GetEventRatingsUseCase by inject()
    val eventTicketStatus: GetEventTicketStatusUseCase by inject()
    val mediaBlobs: com.eventmap.server.domain.repository.MediaBlobRepository by inject()
    val businessRepository: com.eventmap.server.domain.repository.BusinessRepository by inject()
    val questRepository: com.eventmap.server.domain.repository.QuestRepository by inject()
    val updateEvent: com.eventmap.server.domain.usecase.UpdateEventUseCase by inject()
    val deleteEvent: com.eventmap.server.domain.usecase.DeleteEventUseCase by inject()
    val eventRepository: com.eventmap.server.domain.repository.EventRepository by inject()

    routing {
        // Health-check для Render/любого хоста — должен возвращать 200 без БД и авторизации.
        get("/health") { call.respondText("OK") }
        authRoutes(register, login, issueTokens, refreshSession, logout, saveInterests)
        eventRoutes(
            getEvents, getEvent, createEvent, updateEvent, deleteEvent, getOrganizerEvents,
            getFriends, eventParticipants, eventRatings, rateEvent, eventTicketStatus, purchase,
            eventRepository,
        )
        mediaRoutes(presignUpload, confirmMedia, eventMedia, mediaBlobs, config)
        videoRoutes(videoFeed, reactVideo, createVideo)
        pushRoutes(registerPushToken, sendPush, config.adminKey)
        organizerRoutes(getOrganizer, getOrganizerEvents, subscribeOrganizer)
        socialRoutes(getComments, addComment, toggleLike, toggleReminder, attend)
        ticketRoutes(purchase, myTickets, ticketQr, checkinTicket)
        businessRoutes(applyBusiness, myBusiness, pinAnimations, updateBusinessPin, reviewBusiness, addAnimation, config.adminKey)
        questRoutes(createQuest, questsNearby, getQuest, questCheckin, receiptCode, reviewQuest, config.adminKey)
        friendRoutes(sendFriendRequest, respondFriend, getFriends, createInvitation, invitationForGuest, invitationByToken, rsvp, rsvpDashboard, shareLocation, stopSharing, friendLocations, waveToFriend)
        gameRoutes(gameProfile, missions, fog, districtLeaderboard, userLeaderboard, createCapsule, capsulesNearby, openCapsule)
        mapRoutes(pulseService)
        userRoutes(getMyProfile, updateMyProfile, getUserHistory)
        feedRoutes(getFeed, getNotifications, markNotificationsRead, getSubscriptions, getSubscriptionRequests, respondSubscriptionRequest)
        searchRoutes(searchUseCase)
        adminRoutes(businessRepository, questRepository, config.adminKey) // /admin — модерация бизнесов и квестов
    }
}
