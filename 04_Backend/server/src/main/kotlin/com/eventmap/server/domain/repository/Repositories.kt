package com.eventmap.server.domain.repository

import com.eventmap.server.domain.model.Media
import com.eventmap.server.domain.model.Video
import com.eventmap.server.domain.model.Attendance
import com.eventmap.server.domain.model.Badge
import com.eventmap.server.domain.model.BusinessAccount
import com.eventmap.server.domain.model.Mission
import com.eventmap.server.domain.model.TimeCapsule
import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventComment
import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.PinAnimation
import com.eventmap.server.domain.model.Quest
import com.eventmap.server.domain.model.QuestRun
import com.eventmap.server.domain.model.EventQuery
import com.eventmap.server.domain.model.Friend
import com.eventmap.server.domain.model.Invitation
import com.eventmap.server.domain.model.Organizer
import com.eventmap.server.domain.model.Ticket
import com.eventmap.server.domain.model.TicketGuest
import com.eventmap.server.domain.model.TicketOrder
import com.eventmap.server.domain.model.User

interface EventRepository {
    suspend fun search(query: EventQuery): List<Event>
    suspend fun byId(id: String): Event?
    suspend fun byOrganizer(organizerId: String): List<Event>
    suspend fun create(draft: Event): Event
}

interface OrganizerRepository {
    suspend fun byId(id: String): Organizer?
}

interface UserRepository {
    suspend fun byEmail(email: String): User?
    suspend fun create(email: String, name: String, passwordHash: String): User
}

data class RefreshTokenRecord(val tokenHash: String, val userId: String, val expiresAtMs: Long, val revoked: Boolean)

interface RefreshTokenRepository {
    suspend fun save(tokenHash: String, userId: String, expiresAtMs: Long)
    suspend fun find(tokenHash: String): RefreshTokenRecord?
    suspend fun revoke(tokenHash: String)
    suspend fun revokeAllForUser(userId: String)
}

// ===== Рилс-соцслой (D9–D11, API §8.6) =====

interface CommentRepository {
    suspend fun byEvent(eventId: String, viewerId: String?): List<EventComment>
    suspend fun add(eventId: String, authorId: String, body: String, parentId: String?): EventComment
    /** @return новое количество лайков; null — комментарий не найден */
    suspend fun toggleLike(commentId: String, userId: String): Int?
}

interface ReminderRepository {
    /** @return true = напоминание включено, false = выключено */
    suspend fun toggle(userId: String, eventId: String, source: String = "bell"): Boolean
    suspend fun isSet(userId: String, eventId: String): Boolean
}

interface AttendanceRepository {
    suspend fun set(userId: String, eventId: String, type: String)
    suspend fun remove(userId: String, eventId: String)
    suspend fun get(userId: String, eventId: String): Attendance?
    /** going из attendance + анонимные билеты «+N»; результат кэшируется в events.going_count */
    suspend fun countGoing(eventId: String): Long
}

// ===== Бизнес-аккаунты (M, API §8.1) =====

interface BusinessRepository {
    suspend fun apply(
        userId: String,
        bin: String,
        legalName: String,
        brandName: String,
        category: String,
        phone: String?,
        social: String?,
        documents: List<Pair<String, String>>, // kind to fileUrl
    ): BusinessAccount

    suspend fun byUser(userId: String): BusinessAccount?
    suspend fun byId(id: String): BusinessAccount?
    /** Админ-модерация (M6). @return null — заявка не найдена */
    suspend fun review(id: String, approved: Boolean, reason: String?): BusinessAccount?
    /** Пин (M4). @return null — нет аккаунта */
    suspend fun updatePin(userId: String, logoUrl: String?, pinColor: String?, animationId: String?): BusinessAccount?
}

interface PinAnimationRepository {
    suspend fun byCategory(category: String): List<PinAnimation>
    suspend fun byId(id: String): PinAnimation?
    suspend fun add(category: String, name: String, lottieKey: String): PinAnimation
}

// ===== Геймификация (R, API §8.4) =====

interface GameRepository {
    suspend fun badgesOf(userId: String): List<Badge>
    /** Выдать значок один раз (code создаётся при отсутствии). @return true — выдан сейчас */
    suspend fun awardBadgeOnce(userId: String, code: String, title: String, emoji: String?, source: String?): Boolean
    suspend fun missionsOf(userId: String): List<Mission>
    suspend fun districtsStats(userId: String): Pair<Int, Int> // visited to total
    suspend fun visitedDistrictIds(userId: String): List<String>
    suspend fun leaderboard(): List<Triple<String, String, Long>> // districtId, name, visits
}

interface CapsuleRepository {
    suspend fun create(c: TimeCapsule): TimeCapsule
    suspend fun byId(id: String): TimeCapsule?
    /** Капсулы, доступные юзеру в bbox: адресованные ему / его собственные / публичные */
    suspend fun nearbyFor(userId: String, bbox: Bbox?): List<TimeCapsule>
    suspend fun markOpened(id: String, by: String)
}

// ===== Друзья и приглашения (N, API §8.7) =====

interface FriendRepository {
    /** @return false — заявка/дружба уже существует */
    suspend fun request(fromId: String, toId: String): Boolean
    /** @return true — принята; false — заявки не было */
    suspend fun respond(userId: String, otherId: String, accept: Boolean): Boolean
    suspend fun listFor(userId: String): List<Friend>
    suspend fun areFriends(a: String, b: String): Boolean
}

interface InvitationRepository {
    suspend fun create(eventId: String, templateId: String, guestUserIds: List<String>, linkToken: String?): Invitation
    suspend fun byId(id: String): Invitation?
    suspend fun byEvent(eventId: String): Invitation?
    suspend fun byLinkToken(token: String): Invitation?
    /** RSVP юзера из приложения. @return false — гость не найден в списке */
    suspend fun rsvpByUser(invitationId: String, userId: String, answer: String, plusOne: Int): Boolean
    /** RSVP по ссылке (без аккаунта): создаёт гостя с именем */
    suspend fun rsvpByLink(invitationId: String, guestName: String, answer: String, plusOne: Int)
}

// ===== Промо-квесты (Q, API §8.3) =====

interface QuestRepository {
    suspend fun create(quest: Quest): Quest
    /** Активные квесты, у которых есть точка внутри bbox; + мой прогресс */
    suspend fun nearby(bbox: Bbox?, viewerId: String?): List<Quest>
    suspend fun byId(id: String, viewerId: String?): Quest?
    /** Старт прохождения (идемпотентно). @return run */
    suspend fun join(questId: String, userId: String): QuestRun
    /** Отметить точку; @return обновлённый run. Если все точки закрыты — finished + prizeQr */
    suspend fun checkin(runId: String, pointId: String, proofType: String, mediaUrl: String?): QuestRun
    suspend fun review(questId: String, approved: Boolean): Quest?
}

interface XpRepository {
    suspend fun add(userId: String, source: String, sourceId: String?, amount: Int)
    suspend fun totalFor(userId: String): Long
}

// ===== Билеты (P, API §8.2) =====

interface TicketRepository {
    /** Создаёт заказ + qty билетов: buyer, гости с userId, остальные — анонимные (owner NULL) */
    suspend fun createPaidOrder(
        eventId: String,
        buyerId: String,
        guests: List<TicketGuest>,
        amount: Int,
        currency: String,
        provider: String,
    ): TicketOrder

    suspend fun myTickets(userId: String): List<Ticket>
    suspend fun byId(ticketId: String): Ticket?
    /** Чек-ин по QR на входе. @return билет после отметки; null — не найден/не совпал/уже использован */
    suspend fun checkin(ticketId: String, qrSecret: String): Ticket?
}

// ===== Медиа-хранилище (R2/S3, §8 storage) =====

/** Порт object-storage. Реализация — SigV4-presign на чистом JDK (data/storage). */
interface StoragePort {
    /** true, если заданы env S3_ENDPOINT/S3_BUCKET/S3_ACCESS_KEY/S3_SECRET_KEY. */
    val configured: Boolean
    /** Временная ссылка для прямой загрузки файла клиентом (HTTP PUT). */
    fun presignPut(key: String, contentType: String): String
    /** Публичная ссылка для чтения загруженного файла. */
    fun publicUrl(key: String): String
}

interface MediaRepository {
    suspend fun create(
        id: String,
        ownerUserId: String,
        url: String,
        contentType: String,
        kind: String,
        eventId: String?,
    ): Media

    suspend fun byEvent(eventId: String): List<Media>
    suspend fun byId(id: String): Media?
}

// ===== Видео-рилсы (E1/E2 «Сейчас») =====

interface VideoRepository {
    /** Лента «Сейчас»: свежие рилсы; viewerId — для отметки liked. */
    suspend fun feedNow(viewerId: String?): List<Video>

    suspend fun create(
        id: String,
        authorUserId: String,
        eventId: String?,
        mediaUrl: String,
        thumb: String?,
        caption: String,
        category: String,
    ): Video

    /** Тоггл ♡. @return новый счётчик лайков; null — видео не найдено. */
    suspend fun toggleReaction(videoId: String, userId: String): Int?
}

// ===== Push-уведомления (FCM/APNS) =====

interface DeviceTokenRepository {
    /** Регистрирует/обновляет токен устройства (upsert по token). */
    suspend fun register(userId: String, token: String, platform: String)
    suspend fun tokensFor(userId: String): List<String>
    suspend fun delete(token: String)
}

/** Порт отправки пушей. Реализация — FCM HTTP v1 (data/push), включается env-секретами. */
interface NotificationPort {
    /** true, если заданы FCM_PROJECT_ID/FCM_CLIENT_EMAIL/FCM_PRIVATE_KEY. */
    val configured: Boolean
    /** Шлёт пуш на все устройства пользователя. @return число успешных отправок. */
    suspend fun sendToUser(
        userId: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): Int
}
