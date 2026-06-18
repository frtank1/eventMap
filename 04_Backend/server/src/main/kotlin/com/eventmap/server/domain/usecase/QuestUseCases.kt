package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Bbox
import com.eventmap.server.domain.model.Quest
import com.eventmap.server.domain.model.QuestPoint
import com.eventmap.server.domain.model.QuestRun
import com.eventmap.server.domain.repository.BusinessRepository
import com.eventmap.server.domain.repository.QuestRepository
import com.eventmap.server.domain.repository.XpRepository
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ===== Промо-квесты (Q1–Q6, API §8.3) =====
//
// Правила:
//  - создаёт только APPROVED бизнес-аккаунт; 1–10 точек; условия geo|photo|purchase;
//  - квест уходит в moderation → админ активирует;
//  - чек-ин: geo — Haversine ≤ 300 м; photo — обязателен mediaUrl (+гео);
//    purchase — одноразовый QR с чека: HMAC-SHA256(pointId, серверный секрет);
//  - цепочка (orderedChain): точка N доступна после N-1;
//  - XP: точка +15, финиш +100; финиш → prize_qr.

private const val XP_POINT = 15
private const val XP_FINISH = 100
private const val GEO_RADIUS_M = 300.0

/** Код для кассы/чека точки. Бизнес получает через /quests/{id}/points/{pid}/receipt-code */
fun receiptCode(pointId: String, secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(pointId.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}

class CreateQuestUseCase(
    private val quests: QuestRepository,
    private val business: BusinessRepository,
) {
    sealed interface Result {
        data class Ok(val quest: Quest) : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        userId: String,
        title: String,
        emoji: String?,
        orderedChain: Boolean,
        startsAt: String?,
        endsAt: String?,
        prizeTitle: String,
        prizeCount: Int?,
        participantLimit: Int?,
        points: List<QuestPoint>,
    ): Result {
        val biz = business.byUser(userId)
            ?: return Result.Rejected("NO_BUSINESS", "Квесты создаёт бизнес-аккаунт — подай заявку")
        if (biz.status != "approved") {
            return Result.Rejected("NOT_APPROVED", "Бизнес-аккаунт ещё на модерации")
        }
        if (title.isBlank()) return Result.Rejected("BAD_REQUEST", "Название обязательно")
        if (prizeTitle.isBlank()) return Result.Rejected("BAD_REQUEST", "Награда обязательна")
        if (points.size !in 1..10) return Result.Rejected("BAD_POINTS", "Точек должно быть от 1 до 10")
        val badCondition = points.firstOrNull { it.condition !in setOf("geo", "photo", "purchase") }
        if (badCondition != null) return Result.Rejected("BAD_CONDITION", "condition: geo | photo | purchase")
        val quest = quests.create(
            Quest(
                id = "", businessId = biz.id, title = title.trim(), emoji = emoji,
                orderedChain = orderedChain, startsAt = startsAt, endsAt = endsAt,
                prizeTitle = prizeTitle.trim(), prizeCount = prizeCount,
                participantLimit = participantLimit, points = points,
            ),
        )
        return Result.Ok(quest)
    }
}

class GetQuestsNearbyUseCase(private val quests: QuestRepository) {
    suspend operator fun invoke(bbox: Bbox?, viewerId: String?): List<Quest> = quests.nearby(bbox, viewerId)
}

class GetQuestUseCase(private val quests: QuestRepository) {
    suspend operator fun invoke(id: String, viewerId: String?): Quest? = quests.byId(id, viewerId)
}

class CheckinQuestPointUseCase(
    private val quests: QuestRepository,
    private val xp: XpRepository,
    private val secret: String, // HMAC-секрет QR-кодов покупок (ADMIN_KEY)
) {
    sealed interface Result {
        data class Ok(val run: QuestRun, val finished: Boolean, val xpGained: Int) : Result
        data object NotFound : Result
        data class Rejected(val code: String, val message: String) : Result
    }

    suspend operator fun invoke(
        userId: String,
        questId: String,
        pointId: String,
        lat: Double?,
        lng: Double?,
        mediaUrl: String?,
        qrPayload: String?,
    ): Result {
        val quest = quests.byId(questId, userId) ?: return Result.NotFound
        if (quest.status != "active") return Result.Rejected("NOT_ACTIVE", "Квест не активен")
        val point = quest.points.firstOrNull { it.id == pointId } ?: return Result.NotFound
        val run = quest.myRun ?: quests.join(questId, userId)
        if (run.finished) return Result.Rejected("FINISHED", "Квест уже пройден")
        if (point.id in run.checkedPointIds) return Result.Rejected("ALREADY_CHECKED", "Точка уже отмечена")
        if (quest.orderedChain) {
            val prev = quest.points.filter { it.ord < point.ord }
            if (prev.any { it.id !in run.checkedPointIds }) {
                return Result.Rejected("CHAIN_LOCKED", "Сначала пройди предыдущие точки — квест по цепочке")
            }
        }
        when (point.condition) {
            "geo" -> {
                if (lat == null || lng == null) return Result.Rejected("GEO_REQUIRED", "Нужна геолокация")
                val d = haversineMeters(point.lat, point.lng, lat, lng)
                if (d > GEO_RADIUS_M) return Result.Rejected("TOO_FAR", "Ты в ${d.toInt()} м — нужно ≤ ${GEO_RADIUS_M.toInt()} м")
            }
            "photo" -> {
                if (mediaUrl.isNullOrBlank()) return Result.Rejected("PHOTO_REQUIRED", "Нужно фото на месте")
                if (lat == null || lng == null) return Result.Rejected("GEO_REQUIRED", "Фото-чекин требует геолокацию")
                val d = haversineMeters(point.lat, point.lng, lat, lng)
                if (d > GEO_RADIUS_M) return Result.Rejected("TOO_FAR", "Ты в ${d.toInt()} м — фото принимается только на месте")
            }
            "purchase" -> {
                if (qrPayload.isNullOrBlank()) return Result.Rejected("QR_REQUIRED", "Сканируй QR на чеке")
                if (qrPayload != receiptCode(point.id, secret)) {
                    return Result.Rejected("QR_INVALID", "QR не распознан — проверь чек этой точки")
                }
            }
        }
        val updated = quests.checkin(run.id, point.id, point.condition, mediaUrl)
        var gained = XP_POINT
        xp.add(userId, "quest_point", point.id, XP_POINT)
        if (updated.finished) {
            xp.add(userId, "quest_finish", questId, XP_FINISH)
            gained += XP_FINISH
        }
        return Result.Ok(updated, updated.finished, gained)
    }
}

class GetReceiptCodeUseCase(
    private val quests: QuestRepository,
    private val business: BusinessRepository,
    private val secret: String,
) {
    sealed interface Result {
        data class Ok(val code: String) : Result
        data object NotFound : Result
        data object Forbidden : Result
    }

    /** Код для кассы — только владельцу квеста */
    suspend operator fun invoke(userId: String, questId: String, pointId: String): Result {
        val quest = quests.byId(questId, null) ?: return Result.NotFound
        val biz = business.byUser(userId) ?: return Result.Forbidden
        if (quest.businessId != biz.id) return Result.Forbidden
        if (quest.points.none { it.id == pointId }) return Result.NotFound
        return Result.Ok(receiptCode(pointId, secret))
    }
}

class ReviewQuestUseCase(private val quests: QuestRepository) {
    suspend operator fun invoke(questId: String, approved: Boolean): Quest? = quests.review(questId, approved)
}
