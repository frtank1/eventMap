package com.eventmap.quest.ui.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.quest.ui.models.CheckInType
import com.eventmap.quest.ui.models.PointStatus
import com.eventmap.quest.ui.models.QuestItem
import com.eventmap.quest.ui.models.QuestPoint
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.koin.dsl.module

// =====================================================================
//  Q · Квесты — сетевой слой (бэкенд §8.3: /quests/*)
//  DTO зеркалят server/presentation/dto/Dto.kt (QuestDto/QuestPointDto)
// =====================================================================

@Serializable
data class QuestPointNet(
    val id: String = "",
    val ord: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeName: String? = null,
    val condition: String = "geo", // geo | photo | purchase
    val checkedByMe: Boolean = false,
)

@Serializable
data class QuestNet(
    val id: String,
    val businessBrand: String = "",
    val title: String,
    val emoji: String? = null,
    val orderedChain: Boolean = false,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val prizeTitle: String = "",
    val status: String = "active",
    val points: List<QuestPointNet> = emptyList(),
    val finishersCount: Long = 0,
    val myProgress: Int = 0,
    val myFinished: Boolean = false,
    val myPrizeQr: String? = null,
)

@Serializable
data class QuestCheckinBody(
    val lat: Double? = null,
    val lng: Double? = null,
    val mediaUrl: String? = null,
    val qrPayload: String? = null,
)

@Serializable
data class QuestCheckinResult(
    val checkedPoints: Int = 0,
    val totalPoints: Int = 0,
    val finished: Boolean = false,
    val prizeQr: String? = null,
    val xpGained: Int = 0,
)

// ── Фото-чекин: presign → PUT → confirm (§8 storage, зеркало MediaRemoteDataSource) ──

@Serializable
data class PresignBody(val kind: String, val contentType: String)

@Serializable
data class PresignedNet(val mediaId: String, val uploadUrl: String, val publicUrl: String)

@Serializable
data class ConfirmBody(
    val mediaId: String,
    val url: String,
    val contentType: String,
    val kind: String = "image",
    val eventId: String? = null,
)

/**
 * @param apiClient основной клиент (bearer) — quests/presign/confirm.
 * @param uploadClient «голый» клиент (named "upload", БЕЗ Auth) — PUT по presigned-URL:
 *        bearer не входит в подпись SigV4 и ломает запрос.
 */
class QuestRemoteDataSource(
    private val apiClient: HttpClient,
    private val uploadClient: HttpClient,
) {
    suspend fun nearby(): HttpResponse = apiClient.get("quests/nearby")
    suspend fun byId(id: String): HttpResponse = apiClient.get("quests/$id")
    suspend fun checkin(questId: String, pointId: String, body: QuestCheckinBody): HttpResponse =
        apiClient.post("quests/$questId/points/$pointId/checkin") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun presign(body: PresignBody): HttpResponse =
        apiClient.post("media/presign") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun putFile(uploadUrl: String, bytes: ByteArray, contentType: String): HttpResponse =
        uploadClient.put(uploadUrl) {
            header(HttpHeaders.ContentType, contentType)
            setBody(bytes)
        }

    suspend fun confirm(body: ConfirmBody): HttpResponse =
        apiClient.post("media/confirm") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}

interface QuestRepository {
    suspend fun nearby(): KtorResponse<List<QuestNet>>
    suspend fun byId(id: String): KtorResponse<QuestNet>
    suspend fun checkin(questId: String, pointId: String, body: QuestCheckinBody): KtorResponse<QuestCheckinResult>
    /** Фото на месте (Q3): грузит снимок в storage. @return публичный URL для mediaUrl чек-ина */
    suspend fun uploadPhoto(bytes: ByteArray, contentType: String): KtorResponse<String>
}

class QuestRepositoryImpl(private val remote: QuestRemoteDataSource) : QuestRepository {
    override suspend fun nearby(): KtorResponse<List<QuestNet>> = safeRequest { remote.nearby() }
    override suspend fun byId(id: String): KtorResponse<QuestNet> = safeRequest { remote.byId(id) }
    override suspend fun checkin(questId: String, pointId: String, body: QuestCheckinBody): KtorResponse<QuestCheckinResult> =
        safeRequest { remote.checkin(questId, pointId, body) }

    override suspend fun uploadPhoto(bytes: ByteArray, contentType: String): KtorResponse<String> {
        // 1) presign
        val presigned = when (val r = safeRequest<PresignedNet> { remote.presign(PresignBody(kind = "image", contentType = contentType)) }) {
            is KtorResponse.Success -> r.data
            is KtorResponse.Failure -> return r
        }
        // 2) прямой PUT в storage
        val put = try {
            remote.putFile(presigned.uploadUrl, bytes, contentType)
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!put.status.isSuccess()) return KtorResponse.failure(put)
        // 3) confirm (метаданные); тело ответа не нужно
        val confirm = try {
            remote.confirm(ConfirmBody(mediaId = presigned.mediaId, url = presigned.publicUrl, contentType = contentType))
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!confirm.status.isSuccess()) return KtorResponse.failure(confirm)
        return KtorResponse.Success(presigned.publicUrl)
    }
}

val questDataModule = module {
    single { QuestRemoteDataSource(apiClient = get(), uploadClient = get(org.koin.core.qualifier.named("upload"))) }
    single<QuestRepository> { QuestRepositoryImpl(remote = get()) }
}

// =====================================================================
//  Маппинг DTO → UI-модели (models/QuestModels.kt)
// =====================================================================

private fun conditionToType(condition: String): CheckInType = when (condition) {
    "photo" -> CheckInType.Photo
    "purchase" -> CheckInType.Qr
    else -> CheckInType.Geo
}

fun QuestNet.toUi(): QuestItem {
    val sorted = points.sortedBy { it.ord }
    val uiPoints = sorted.mapIndexed { i, p ->
        val status = when {
            p.checkedByMe -> PointStatus.Done
            // цепочка: активна первая неотмеченная; без цепочки — все неотмеченные активны
            !orderedChain -> PointStatus.Active
            sorted.take(i).all { it.checkedByMe } -> PointStatus.Active
            else -> PointStatus.Locked
        }
        QuestPoint(
            id = p.id,
            title = p.placeName ?: "Точка ${i + 1}",
            address = p.placeName.orEmpty(),
            checkInType = conditionToType(p.condition),
            status = status,
            distanceM = null, // расстояние считает карта; здесь без геолокации
            questId = id,
            lat = p.lat,
            lng = p.lng,
        )
    }
    return QuestItem(
        id = id,
        emoji = emoji ?: "🎮",
        title = title,
        authorName = businessBrand.ifEmpty { "Бизнес" },
        isVerifiedBusiness = true, // квесты создаёт только approved-бизнес (§8.3)
        pointsTotal = sorted.size,
        pointsDone = myProgress,
        checkInType = uiPoints.firstOrNull { it.status == PointStatus.Active }?.checkInType
            ?: uiPoints.firstOrNull()?.checkInType ?: CheckInType.Geo,
        deadlineLabel = endsAt?.take(10)?.let { "до $it" } ?: "бессрочно",
        finishersCount = finishersCount.toInt(),
        rewardLabel = prizeTitle.ifEmpty { "Приз" },
        points = uiPoints,
    )
}
