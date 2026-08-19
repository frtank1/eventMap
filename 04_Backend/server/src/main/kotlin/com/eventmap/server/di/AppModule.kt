package com.eventmap.server.di

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.repository.AttendanceRepositoryImpl
import com.eventmap.server.data.repository.BusinessRepositoryImpl
import com.eventmap.server.data.repository.CommentRepositoryImpl
import com.eventmap.server.data.repository.PinAnimationRepositoryImpl
import com.eventmap.server.data.repository.EventRepositoryImpl
import com.eventmap.server.data.repository.OrganizerRepositoryImpl
import com.eventmap.server.data.repository.RefreshTokenRepositoryImpl
import com.eventmap.server.data.repository.ReminderRepositoryImpl
import com.eventmap.server.data.repository.CapsuleRepositoryImpl
import com.eventmap.server.data.repository.FriendRepositoryImpl
import com.eventmap.server.data.repository.GameRepositoryImpl
import com.eventmap.server.domain.repository.CapsuleRepository
import com.eventmap.server.domain.repository.GameRepository
import com.eventmap.server.domain.usecase.CreateCapsuleUseCase
import com.eventmap.server.domain.usecase.GetCapsulesNearbyUseCase
import com.eventmap.server.domain.usecase.GetDistrictLeaderboardUseCase
import com.eventmap.server.domain.usecase.GetFogUseCase
import com.eventmap.server.domain.usecase.GetGameProfileUseCase
import com.eventmap.server.domain.usecase.GetMissionsUseCase
import com.eventmap.server.domain.usecase.OpenCapsuleUseCase
import com.eventmap.server.data.repository.InvitationRepositoryImpl
import com.eventmap.server.data.repository.QuestRepositoryImpl
import com.eventmap.server.domain.repository.FriendRepository
import com.eventmap.server.domain.repository.InvitationRepository
import com.eventmap.server.domain.usecase.CreateInvitationUseCase
import com.eventmap.server.domain.usecase.GetFriendsUseCase
import com.eventmap.server.domain.usecase.GetInvitationByTokenUseCase
import com.eventmap.server.domain.usecase.GetInvitationForGuestUseCase
import com.eventmap.server.domain.usecase.GetRsvpDashboardUseCase
import com.eventmap.server.domain.usecase.RespondFriendRequestUseCase
import com.eventmap.server.domain.usecase.RsvpUseCase
import com.eventmap.server.domain.usecase.SendFriendRequestUseCase
import com.eventmap.server.data.repository.TicketRepositoryImpl
import com.eventmap.server.data.repository.UserRepositoryImpl
import com.eventmap.server.data.repository.XpRepositoryImpl
import com.eventmap.server.domain.repository.QuestRepository
import com.eventmap.server.domain.repository.XpRepository
import com.eventmap.server.domain.usecase.CheckinQuestPointUseCase
import com.eventmap.server.domain.usecase.CreateQuestUseCase
import com.eventmap.server.domain.usecase.GetQuestUseCase
import com.eventmap.server.domain.usecase.GetQuestsNearbyUseCase
import com.eventmap.server.domain.usecase.GetReceiptCodeUseCase
import com.eventmap.server.domain.usecase.ReviewQuestUseCase
import com.eventmap.server.domain.repository.AttendanceRepository
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.CommentRepository
import com.eventmap.server.domain.repository.PinAnimationRepository
import com.eventmap.server.domain.usecase.AddPinAnimationUseCase
import com.eventmap.server.domain.usecase.ApplyBusinessUseCase
import com.eventmap.server.domain.usecase.GetMyBusinessUseCase
import com.eventmap.server.domain.usecase.GetPinAnimationsUseCase
import com.eventmap.server.domain.usecase.ReviewBusinessUseCase
import com.eventmap.server.domain.usecase.UpdateBusinessPinUseCase
import com.eventmap.server.domain.repository.EventRepository
import com.eventmap.server.domain.repository.OrganizerRepository
import com.eventmap.server.domain.repository.RefreshTokenRepository
import com.eventmap.server.domain.repository.ReminderRepository
import com.eventmap.server.domain.repository.TicketRepository
import com.eventmap.server.domain.repository.UserRepository
import com.eventmap.server.domain.usecase.CheckinTicketUseCase
import com.eventmap.server.domain.usecase.GetMyTicketsUseCase
import com.eventmap.server.domain.usecase.GetTicketQrUseCase
import com.eventmap.server.domain.usecase.PurchaseTicketsUseCase
import com.eventmap.server.domain.usecase.AddCommentUseCase
import com.eventmap.server.domain.usecase.AttendUseCase
import com.eventmap.server.domain.usecase.CreateEventUseCase
import com.eventmap.server.domain.usecase.GetCommentsUseCase
import com.eventmap.server.domain.usecase.ToggleCommentLikeUseCase
import com.eventmap.server.domain.usecase.ToggleReminderUseCase
import com.eventmap.server.domain.usecase.GetEventUseCase
import com.eventmap.server.domain.usecase.GetEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerEventsUseCase
import com.eventmap.server.domain.usecase.GetOrganizerUseCase
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import com.eventmap.server.security.JwtService
import org.koin.dsl.module

val appModule = module {
    single { AppConfig() }
    single { JwtService(get()) }
    // Хостинг медиа: R2/S3 при заданных S3_* env, иначе фолбэк в Postgres (V10).
    single<com.eventmap.server.domain.repository.StoragePort> {
        val s3 = com.eventmap.server.data.storage.StorageService(get())
        if (s3.configured) s3 else com.eventmap.server.data.storage.DbStorageService(get())
    }
    single<com.eventmap.server.domain.repository.MediaBlobRepository> { com.eventmap.server.data.storage.MediaBlobRepositoryImpl() }
    single<com.eventmap.server.domain.repository.MediaRepository> { com.eventmap.server.data.repository.MediaRepositoryImpl() }
    single<com.eventmap.server.domain.repository.VideoRepository> { com.eventmap.server.data.repository.VideoRepositoryImpl() }
    single<com.eventmap.server.domain.repository.DeviceTokenRepository> { com.eventmap.server.data.repository.DeviceTokenRepositoryImpl() }
    single<com.eventmap.server.domain.repository.NotificationPort> { com.eventmap.server.data.push.FcmPushSender(get(), get()) }

    single<EventRepository> { EventRepositoryImpl() }
    single<OrganizerRepository> { OrganizerRepositoryImpl() }
    single<UserRepository> { UserRepositoryImpl() }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl() }
    single<CommentRepository> { CommentRepositoryImpl() }
    single<ReminderRepository> { ReminderRepositoryImpl() }
    single<AttendanceRepository> { AttendanceRepositoryImpl() }
    single<TicketRepository> { TicketRepositoryImpl() }
    single<BusinessRepository> { BusinessRepositoryImpl() }
    single<PinAnimationRepository> { PinAnimationRepositoryImpl() }
    single<QuestRepository> { QuestRepositoryImpl() }
    single<XpRepository> { XpRepositoryImpl() }
    single<FriendRepository> { FriendRepositoryImpl() }
    single<com.eventmap.server.domain.repository.FriendLocationRepository> { com.eventmap.server.data.repository.FriendLocationRepositoryImpl() } // V12
    single<InvitationRepository> { InvitationRepositoryImpl() }
    single<GameRepository> { GameRepositoryImpl() }
    single<CapsuleRepository> { CapsuleRepositoryImpl() }
    single { com.eventmap.server.data.pulse.PulseService(get<AppConfig>().adminKey) }

    // V9: подписки/уведомления/оценки
    single<com.eventmap.server.domain.repository.SubscriptionRepository> { com.eventmap.server.data.repository.SubscriptionRepositoryImpl() }
    single<com.eventmap.server.domain.repository.NotificationRepository> { com.eventmap.server.data.repository.NotificationRepositoryImpl() }
    single<com.eventmap.server.domain.repository.RatingRepository> { com.eventmap.server.data.repository.RatingRepositoryImpl() }

    factory { GetEventsUseCase(get()) }
    factory { GetEventUseCase(get()) }
    factory { CreateEventUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.UpdateEventUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.DeleteEventUseCase(get()) }
    factory { GetOrganizerUseCase(get()) }
    factory { GetOrganizerEventsUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { IssueTokensUseCase(get(), get()) }
    factory { RefreshSessionUseCase(get(), get()) }
    factory { LogoutUseCase(get()) }

    // рилс-соцслой (D9–D11) + присутствие
    factory { GetCommentsUseCase(get()) }
    factory { AddCommentUseCase(get()) }
    factory { ToggleCommentLikeUseCase(get()) }
    factory { ToggleReminderUseCase(get()) }
    factory { AttendUseCase(get(), get()) }

    // медиа-хранилище (§8 storage)
    factory { com.eventmap.server.domain.usecase.PresignUploadUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.ConfirmMediaUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetEventMediaUseCase(get()) }

    // видео-рилсы (E1/E2)
    factory { com.eventmap.server.domain.usecase.GetVideoFeedUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.ReactVideoUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.CreateVideoUseCase(get(), get()) }

    // push (FCM/APNS)
    factory { com.eventmap.server.domain.usecase.RegisterPushTokenUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.SendPushUseCase(get()) }

    // билеты с гостями (P, §8.2)
    factory { PurchaseTicketsUseCase(get(), get(), get(), get(), get()) }
    factory { GetMyTicketsUseCase(get()) }
    factory { GetTicketQrUseCase(get()) }
    factory { CheckinTicketUseCase(get(), get()) }

    // бизнес-аккаунты (M, §8.1)
    factory { ApplyBusinessUseCase(get()) }
    factory { GetMyBusinessUseCase(get()) }
    factory { GetPinAnimationsUseCase(get(), get()) }
    factory { UpdateBusinessPinUseCase(get(), get()) }
    factory { ReviewBusinessUseCase(get()) }
    factory { AddPinAnimationUseCase(get()) }

    // промо-квесты (Q, §8.3)
    factory { CreateQuestUseCase(get(), get()) }
    factory { GetQuestsNearbyUseCase(get()) }
    factory { GetQuestUseCase(get()) }
    factory { CheckinQuestPointUseCase(get(), get(), get<AppConfig>().adminKey, get()) }
    factory { GetReceiptCodeUseCase(get(), get(), get<AppConfig>().adminKey) }
    factory { ReviewQuestUseCase(get()) }

    // друзья и приглашения (N, §8.7)
    factory { SendFriendRequestUseCase(get(), get(), get()) }
    factory { RespondFriendRequestUseCase(get()) }
    factory { GetFriendsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.ShareMyLocationUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.StopSharingLocationUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetFriendLocationsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.WaveToFriendUseCase(get(), get(), get(), get()) }
    factory { CreateInvitationUseCase(get(), get(), get()) }
    factory { GetInvitationForGuestUseCase(get()) }
    factory { GetInvitationByTokenUseCase(get()) }
    factory { RsvpUseCase(get()) }
    factory { GetRsvpDashboardUseCase(get(), get()) }

    // геймификация (R, §8.4)
    factory { GetGameProfileUseCase(get(), get()) }
    factory { GetMissionsUseCase(get()) }
    factory { GetFogUseCase(get()) }
    factory { GetDistrictLeaderboardUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetUserLeaderboardUseCase(get(), get(), get()) }
    factory { CreateCapsuleUseCase(get(), get()) }
    factory { GetCapsulesNearbyUseCase(get()) }
    factory { OpenCapsuleUseCase(get()) }

    // V9: профиль H1, лента/уведомления F4, подписки F1, поиск G2, оценки P4
    factory { com.eventmap.server.domain.usecase.GetMyProfileUseCase(get(), get(), get(), get()) }
    factory { com.eventmap.server.domain.usecase.UpdateMyProfileUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.SaveInterestsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetUserHistoryUseCase(get(), get()) }
    factory { com.eventmap.server.domain.usecase.GetEventParticipantsUseCase(get(), get()) }
    factory { com.eventmap.server.domain.usecase.GetFeedUseCase(get(), get()) }
    factory { com.eventmap.server.domain.usecase.GetNotificationsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.MarkNotificationsReadUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetSubscriptionsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.SubscribeOrganizerUseCase(get(), get()) }
    factory { com.eventmap.server.domain.usecase.GetSubscriptionRequestsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.RespondSubscriptionRequestUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.SearchUseCase(get(), get(), get()) }
    factory { com.eventmap.server.domain.usecase.RateEventUseCase(get(), get()) }
    factory { com.eventmap.server.domain.usecase.GetEventRatingsUseCase(get()) }
    factory { com.eventmap.server.domain.usecase.GetEventTicketStatusUseCase(get()) }
}
