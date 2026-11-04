package com.eventmap.server.data.db

import org.jetbrains.exposed.sql.Table

object OrganizersTable : Table("organizers") {
    val id = varchar("id", 64)
    val name = varchar("name", 255)
    val bio = text("bio").default("")
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val isVerified = bool("is_verified").default(false)
    val subscribers = integer("subscribers").default(0)
    override val primaryKey = PrimaryKey(id)
}

// ===== Видео-рилсы · миграция V7 =====
object VideosTable : Table("videos") {
    val id = varchar("id", 64)
    val authorUserId = varchar("author_user_id", 64).index()
    val eventId = varchar("event_id", 64).nullable().index()
    val mediaUrl = varchar("media_url", 1024)
    val mediaId = varchar("media_id", 64).nullable()
    val thumbUrl = varchar("thumb_url", 1024).nullable()
    val caption = varchar("caption", 500).default("")
    val category = varchar("category", 64).default("")
    val viewers = integer("viewers").default(0)
    val likeCount = integer("like_count").default(0)
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(id)
}

object VideoLikesTable : Table("video_likes") {
    val videoId = varchar("video_id", 64)
    val userId = varchar("user_id", 64)
    override val primaryKey = PrimaryKey(videoId, userId)
}

// ===== Push-токены устройств · миграция V8 =====
object DeviceTokensTable : Table("device_tokens") {
    val token = varchar("token", 512)
    val userId = varchar("user_id", 64).index()
    val platform = varchar("platform", 16).default("android")
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(token)
}

// Фолбэк-хранилище медиа в Postgres (S3 не настроен) · миграция V10
object MediaBlobsTable : Table("media_blobs") {
    val key = varchar("key", 255)
    val contentType = varchar("content_type", 128)
    val bytes = binary("bytes")
    val size = integer("size").default(0)
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(key)
}

// ===== Медиа (R2/S3) · миграция V6 =====
object MediaTable : Table("media") {
    val id = varchar("id", 64)
    val ownerUserId = varchar("owner_user_id", 64).index()
    val url = varchar("url", 1024)
    val contentType = varchar("content_type", 128).default("")
    val kind = varchar("kind", 16).default("image")
    val provider = varchar("provider", 16).default("storage")
    val status = varchar("status", 16).default("ready")
    val playbackId = varchar("playback_id", 255).nullable()
    val eventId = varchar("event_id", 64).nullable().index()
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(id)
}

object MuxUploadsTable : Table("mux_uploads") {
    val mediaId = varchar("media_id", 64)
    val uploadId = varchar("upload_id", 255).nullable()
    val assetId = varchar("asset_id", 255).nullable()
    val confirmed = bool("confirmed").default(false)
    val errorCode = varchar("error_code", 64).nullable()
    val createdAtMs = long("created_at_ms")
    override val primaryKey = PrimaryKey(mediaId)
}

object MuxWebhookEventsTable : Table("mux_webhook_events") {
    val id = varchar("id", 255)
    val receivedAtMs = long("received_at_ms")
    override val primaryKey = PrimaryKey(id)
}

object EventsTable : Table("events") {
    val id = varchar("id", 64)
    val title = varchar("title", 255)
    val description = text("description").default("")
    val category = varchar("category", 64).default("")
    val status = varchar("status", 16).default("soon")
    val startAt = varchar("start_at", 40).default("")
    val endAt = varchar("end_at", 40).default("")
    val lat = double("lat").default(0.0)
    val lng = double("lng").default(0.0)
    val locationName = varchar("location_name", 255).default("")
    val priceType = varchar("price_type", 16).default("free")
    val priceAmount = integer("price_amount").default(0)
    val currency = varchar("currency", 8).default("KZT")
    val goingCount = integer("going_count").default(0)
    val liveViewers = integer("live_viewers").default(0)
    val ratingAvg = double("rating_avg").default(0.0)
    val ratingCount = integer("rating_count").default(0)
    val cover = varchar("cover", 512).nullable()
    val organizerId = varchar("organizer_id", 64).nullable()
    val capacity = integer("capacity").nullable()

    // ревизия 11.06.2026 (миграция V2)
    val isAdult = bool("is_adult").default(false)
    val visibility = varchar("visibility", 16).default("public") // public | private (раздел N)
    val geoRadiusM = integer("geo_radius_m").default(300)        // радиус «Я тоже здесь»
    // Event V2 · V13: один Event для афиши, сбора людей, приватных встреч и live-сигналов.
    val kind = varchar("kind", 20).default("planned")
    val sourceType = varchar("source_type", 20).default("initiator")
    val joinPolicy = varchar("join_policy", 20).default("open")
    val audience = varchar("audience", 20).default("everyone")
    val minAge = integer("min_age").nullable()
    val expiresAtMs = long("expires_at_ms").nullable()
    val proofMediaId = varchar("proof_media_id", 64).nullable()
    val confirmationCount = integer("confirmation_count").default(0)
    val interestCount = integer("interest_count").default(0)
    override val primaryKey = PrimaryKey(id)
}

object EventSignalsTable : Table("event_signals") {
    val eventId = varchar("event_id", 64).index()
    val userId = varchar("user_id", 64)
    val signalType = varchar("signal_type", 16) // confirm | interest
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(eventId, userId, signalType)
}

object EventJoinRequestsTable : Table("event_join_requests") {
    val eventId = varchar("event_id", 64).index()
    val userId = varchar("user_id", 64)
    val status = varchar("status", 16).default("pending") // pending|approved|rejected
    val message = varchar("message", 500).default("")
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(eventId, userId)
}

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255).default("")
    val passwordHash = varchar("password_hash", 255)

    // профиль H1 · миграция V9
    val username = varchar("username", 64).default("")
    val bio = text("bio").default("")
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val city = varchar("city", 64).default("")
    val interests = text("interests").default("") // csv категорий (A8)
    override val primaryKey = PrimaryKey(id)
}

// ===== Профиль/подписки/уведомления/оценки · миграция V9 =====

object OrganizerSubscriptionsTable : Table("organizer_subscriptions") {
    val userId = varchar("user_id", 64)
    val organizerId = varchar("organizer_id", 64).index()
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(userId, organizerId)
}

object SubscriptionRequestsTable : Table("subscription_requests") {
    val id = varchar("id", 64)
    val targetUserId = varchar("target_user_id", 64).index()
    val fromUserId = varchar("from_user_id", 64)
    val status = varchar("status", 16).default("pending") // pending|approved|rejected
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(id)
}

object NotificationsTable : Table("notifications") {
    val id = varchar("id", 64)
    val userId = varchar("user_id", 64).index()
    val type = varchar("type", 16).default("info") // info|event|subscribe|live
    val text = text("text")
    val createdAtMs = long("created_at_ms").default(0)
    val read = bool("read").default(false)
    override val primaryKey = PrimaryKey(id)
}

object EventRatingsTable : Table("event_ratings") {
    val id = varchar("id", 64)
    val eventId = varchar("event_id", 64).index()
    val userId = varchar("user_id", 64)
    val value = integer("value")
    val review = text("review").default("")
    val tags = text("tags").default("") // csv тегов P4 · V11
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(eventId, userId) }
}

// ===== Промо-квесты (Q, API §8.3) · миграция V3 =====

object QuestsTable : Table("quests") {
    val id = varchar("id", 64)
    val businessId = varchar("business_id", 64).index()
    val title = varchar("title", 255)
    val emoji = varchar("emoji", 8).nullable()
    val orderedChain = bool("ordered_chain").default(false)
    val startsAt = varchar("starts_at", 40).nullable() // ISO, стиль events
    val endsAt = varchar("ends_at", 40).nullable()
    val prizeTitle = varchar("prize_title", 255)
    val prizeCount = integer("prize_count").nullable()
    val participantLimit = integer("participant_limit").nullable()
    val status = varchar("status", 16).default("moderation") // moderation|active|ended|rejected
    override val primaryKey = PrimaryKey(id)
}

object QuestPointsTable : Table("quest_points") {
    val id = varchar("id", 64)
    val questId = varchar("quest_id", 64).index()
    val ord = integer("ord")
    val lat = double("lat")
    val lng = double("lng")
    val placeName = varchar("place_name", 255).nullable()
    val condition = varchar("condition", 16) // geo|photo|purchase
    override val primaryKey = PrimaryKey(id)
}

object QuestRunsTable : Table("quest_runs") {
    val id = varchar("id", 64)
    val questId = varchar("quest_id", 64).index()
    val userId = varchar("user_id", 64).index()
    val startedAtMs = long("started_at_ms").default(0)
    val finishedAtMs = long("finished_at_ms").nullable()
    val prizeQr = varchar("prize_qr", 128).nullable()
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(questId, userId) }
}

object QuestCheckinsTable : Table("quest_checkins") {
    val runId = varchar("run_id", 64)
    val pointId = varchar("point_id", 64)
    val proofType = varchar("proof_type", 16) // geo|photo|receipt_qr
    val mediaUrl = varchar("media_url", 512).nullable()
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(runId, pointId)
}

// XP (R, §8.4) — квесты начисляют сюда
object XpEventsTable : Table("xp_events") {
    val id = varchar("id", 64)
    val userId = varchar("user_id", 64).index()
    val sourceType = varchar("source", 32) // checkin|quest_point|quest_finish|district|mission
    val sourceId = varchar("source_id", 64).nullable()
    val amount = integer("amount")
    override val primaryKey = PrimaryKey(id)
}

// ===== Геймификация (R, API §8.4) · миграция V3 =====

object BadgesTable : Table("badges") {
    val id = varchar("id", 64)
    val code = varchar("code", 64).uniqueIndex()
    val title = varchar("title", 128)
    val emoji = varchar("emoji", 8).nullable()
    override val primaryKey = PrimaryKey(id)
}

object UserBadgesTable : Table("user_badges") {
    val userId = varchar("user_id", 64)
    val badgeId = varchar("badge_id", 64)
    val sourceType = varchar("source", 64).nullable()
    override val primaryKey = PrimaryKey(userId, badgeId)
}

object MissionsTable : Table("missions") {
    val id = varchar("id", 64)
    val userId = varchar("user_id", 64).index()
    val kind = varchar("kind", 32)
    val payload = text("payload").nullable()
    val progress = integer("progress").default(0)
    val target = integer("target")
    val rewardXp = integer("reward_xp").default(0)
    val multiplier = float("multiplier").default(1f)
    val status = varchar("status", 16).default("active")
    val expiresAtMs = long("expires_at_ms").nullable()
    override val primaryKey = PrimaryKey(id)
}

object DistrictsTable : Table("districts") {
    val id = varchar("id", 64)
    val city = varchar("city", 64).default("astana")
    val name = varchar("name", 128)
    val geojson = text("geojson")
    override val primaryKey = PrimaryKey(id)
}

object DistrictVisitsTable : Table("district_visits") { // туман войны G3
    val userId = varchar("user_id", 64)
    val districtId = varchar("district_id", 64)
    override val primaryKey = PrimaryKey(userId, districtId)
}

object TimeCapsulesTable : Table("time_capsules") { // G15
    val id = varchar("id", 64)
    val ownerId = varchar("owner_id", 64).index()
    val lat = double("lat")
    val lng = double("lng")
    val mediaUrl = varchar("media_url", 512).nullable()
    val bodyText = text("body_text").nullable()
    val recipientType = varchar("recipient_type", 16) // user|self_1y|public
    val recipientId = varchar("recipient_id", 64).nullable()
    val opensAtMs = long("opens_at_ms").nullable()
    val openedAtMs = long("opened_at_ms").nullable()
    val openedBy = varchar("opened_by", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

// ===== Рилс-соцслой (D9–D11) + присутствие · миграция V4 =====

// «иду» (going) до/во время события · «был» (was) — история
object AttendanceTable : Table("attendance") {
    val userId = varchar("user_id", 64)
    val eventId = varchar("event_id", 64)
    val type = varchar("type", 8) // going | was
    override val primaryKey = PrimaryKey(userId, eventId)
}

object EventCommentsTable : Table("event_comments") {
    val id = varchar("id", 64)
    val eventId = varchar("event_id", 64).index()
    val authorId = varchar("author_id", 64)
    val parentId = varchar("parent_id", 64).nullable() // ответы — один уровень вложенности
    val body = text("body")
    val likeCount = integer("like_count").default(0)
    val createdAtMs = long("created_at_ms").default(0)
    override val primaryKey = PrimaryKey(id)
}

object CommentLikesTable : Table("comment_likes") {
    val commentId = varchar("comment_id", 64)
    val userId = varchar("user_id", 64)
    override val primaryKey = PrimaryKey(commentId, userId)
}

// Колокольчик D11: source=bell (вручную) | ticket (авто после оплаты)
object RemindersTable : Table("reminders") {
    val userId = varchar("user_id", 64)
    val eventId = varchar("event_id", 64)
    val sourceType = varchar("source", 16).default("bell") // имя проперти ≠ source (занято ColumnSet)
    override val primaryKey = PrimaryKey(userId, eventId)
}

// ===== Бизнес-аккаунты (M, API §8.1) · миграция V2 =====

object BusinessAccountsTable : Table("business_accounts") {
    val id = varchar("id", 64)
    val userId = varchar("user_id", 64).uniqueIndex()
    val bin = varchar("bin", 12)
    val legalName = varchar("legal_name", 255)
    val brandName = varchar("brand_name", 255)
    val category = varchar("category", 64)
    val phone = varchar("phone", 32).nullable()
    val social = varchar("social", 255).nullable()
    val status = varchar("status", 16).default("pending") // pending|approved|rejected
    val rejectReason = text("reject_reason").nullable()
    val logoUrl = varchar("logo_url", 512).nullable()
    val pinColor = varchar("pin_color", 9).default("#FF5C8A")
    val animationId = varchar("animation_id", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object BusinessDocumentsTable : Table("business_documents") {
    val id = varchar("id", 64)
    val businessId = varchar("business_id", 64).index()
    val kind = varchar("kind", 32) // registration|license|other
    val fileUrl = varchar("file_url", 512)
    override val primaryKey = PrimaryKey(id)
}

// Наборы анимаций пина: грузит админ, бизнес видит только свою категорию
object PinAnimationsTable : Table("pin_animations") {
    val id = varchar("id", 64)
    val category = varchar("category", 64).index()
    val name = varchar("name", 128)
    val lottieKey = varchar("lottie_key", 512)
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

// ===== Билеты с гостями (P, API §8.2) · миграция V2 =====
// goingCount события = attendance(going) + билеты с owner_user_id IS NULL (анонимные «+N»)

object TicketOrdersTable : Table("ticket_orders") {
    val id = varchar("id", 64)
    val eventId = varchar("event_id", 64).index()
    val buyerId = varchar("buyer_id", 64)
    val qty = integer("qty")
    val amount = integer("amount")
    val currency = varchar("currency", 8).default("KZT")
    val provider = varchar("provider", 16) // kaspi | card | applepay | mock
    val status = varchar("status", 16).default("pending") // pending|paid|failed|refunded
    override val primaryKey = PrimaryKey(id)
}

object TicketsTable : Table("tickets") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64)
    val eventId = varchar("event_id", 64).index()
    val ownerUserId = varchar("owner_user_id", 64).nullable() // NULL = анонимный гость
    val qrSecret = varchar("qr_secret", 128).uniqueIndex()
    val status = varchar("status", 16).default("active") // active|used|refunded
    override val primaryKey = PrimaryKey(id)
}

// ===== Друзья и приглашения (N, API §8.7) · миграция V4 =====

// Гео-шеринг друзей (C10/L5) · миграция V12. Именная позиция, только для друзей, TTL 15 мин.
object FriendLocationsTable : Table("friend_locations") {
    val userId = varchar("user_id", 64)
    val lat = double("lat")
    val lng = double("lng")
    val updatedAtMs = long("updated_at_ms").default(0)
    override val primaryKey = PrimaryKey(userId)
}

// Каноничный порядок: userA < userB (лексикографически)
object FriendshipsTable : Table("friendships") {
    val userA = varchar("user_a", 64)
    val userB = varchar("user_b", 64)
    val status = varchar("status", 16).default("pending") // pending|accepted
    val requestedBy = varchar("requested_by", 64)
    override val primaryKey = PrimaryKey(userA, userB)
}

object FriendGroupsTable : Table("friend_groups") {
    val id = varchar("id", 64)
    val ownerId = varchar("owner_id", 64).index()
    val name = varchar("name", 128)
    override val primaryKey = PrimaryKey(id)
}

object FriendGroupMembersTable : Table("friend_group_members") {
    val groupId = varchar("group_id", 64)
    val friendId = varchar("friend_id", 64)
    override val primaryKey = PrimaryKey(groupId, friendId)
}

object InvitationsTable : Table("invitations") {
    val id = varchar("id", 64)
    val eventId = varchar("event_id", 64).index()
    val templateId = varchar("template_id", 64)
    val linkToken = varchar("link_token", 128).nullable().uniqueIndex() // веб-открытка без аккаунта
    override val primaryKey = PrimaryKey(id)
}

object InvitationGuestsTable : Table("invitation_guests") {
    val id = varchar("id", 64)
    val invitationId = varchar("invitation_id", 64).index()
    val userId = varchar("user_id", 64).nullable() // NULL = гость по ссылке
    val guestName = varchar("guest_name", 128).nullable()
    val rsvp = varchar("rsvp", 8).nullable() // yes|no|NULL (молчит)
    val plusOne = integer("plus_one").default(0)
    val respondedAtMs = long("responded_at_ms").nullable()
    override val primaryKey = PrimaryKey(id)
}

// ===== Пульс карты (G1/G2/G9, API §8.5) · миграция V5 =====
// ПРИВАТНОСТЬ: user_hash = HMAC(userId, daily salt); TTL 15 мин (чистит джоба); наружу — только агрегаты

object LocationPingsTable : Table("location_pings") {
    val userHash = varchar("user_hash", 64)
    val lat = double("lat")
    val lng = double("lng")
    val createdAtMs = long("created_at_ms").default(0)
}

object PulseAggregatesTable : Table("pulse_aggregates") {
    val eventId = varchar("event_id", 64)
    val glow = float("glow").default(0f)       // 0..1 → PulseGlow (G1)
    val crowd10m = integer("crowd_10m").default(0) // поток (G2)
    val viewers = integer("viewers").default(0)
    val updatedAtMs = long("updated_at_ms").default(0)
    override val primaryKey = PrimaryKey(eventId)
}

object PulseAnomaliesTable : Table("pulse_anomalies") { // G9 «здесь что-то происходит»
    val id = varchar("id", 64)
    val lat = double("lat")
    val lng = double("lng")
    val userCount = integer("user_count")
    val startedAtMs = long("started_at_ms").default(0)
    val endedAtMs = long("ended_at_ms").nullable()
    override val primaryKey = PrimaryKey(id)
}

// Храним SHA-256 хеш refresh-токена (сам токен — только у клиента).
object RefreshTokensTable : Table("refresh_tokens") {
    val tokenHash = varchar("token_hash", 64)
    val userId = varchar("user_id", 64).index()
    val expiresAtMs = long("expires_at_ms")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(tokenHash)
}
