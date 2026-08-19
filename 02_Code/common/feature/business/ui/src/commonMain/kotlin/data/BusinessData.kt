package com.eventmap.business.ui.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
//  M · Бизнес-аккаунты — сетевой слой (бэкенд §8.1: /business/*)
//  + создание промо-квеста (§8.3: POST /quests, только approved-бизнес)
//  DTO зеркалят server/presentation/dto/Dto.kt
// =====================================================================

@Serializable
data class BusinessDocNet(val kind: String, val fileUrl: String)

@Serializable
data class BusinessApplyBody(
    val bin: String,
    val legalName: String,
    val brandName: String,
    val category: String,
    val phone: String? = null,
    val social: String? = null,
    val documents: List<BusinessDocNet> = emptyList(),
)

@Serializable
data class BusinessNet(
    val id: String = "",
    val status: String = "pending", // pending | approved | rejected
    val rejectReason: String? = null,
    val bin: String = "",
    val legalName: String = "",
    val brandName: String = "",
    val category: String = "",
    val phone: String? = null,
    val social: String? = null,
    val logoUrl: String? = null,
    val pinColor: String = "#FF5C8A",
    val animationId: String? = null,
)

@Serializable
data class UpdatePinBody(val logoUrl: String? = null, val pinColor: String? = null, val animationId: String? = null)

// ── Квест (создание бизнесом) ──

@Serializable
data class QuestPointBody(
    val lat: Double,
    val lng: Double,
    val placeName: String? = null,
    val condition: String, // geo | photo | purchase
)

@Serializable
data class CreateQuestBody(
    val title: String,
    val emoji: String? = null,
    val orderedChain: Boolean = false,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val prizeTitle: String,
    val prizeCount: Int? = null,
    val participantLimit: Int? = null,
    val points: List<QuestPointBody>,
)

@Serializable
data class CreatedQuestNet(val id: String = "", val status: String = "moderation", val title: String = "")

// ── Документы: presign → PUT → confirm (зеркало QuestData.uploadPhoto) ──

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
 * @param apiClient основной клиент (bearer).
 * @param uploadClient «голый» клиент (named "upload", БЕЗ Auth) — PUT по presigned-URL:
 *        bearer не входит в подпись SigV4 и ломает запрос.
 */
class BusinessRemoteDataSource(
    private val apiClient: HttpClient,
    private val uploadClient: HttpClient,
) {
    suspend fun apply(body: BusinessApplyBody): HttpResponse =
        apiClient.post("business/apply") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun me(): HttpResponse = apiClient.get("business/me")

    suspend fun updatePin(body: UpdatePinBody): HttpResponse =
        apiClient.patch("business/pin") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun createQuest(body: CreateQuestBody): HttpResponse =
        apiClient.post("quests") {
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

interface BusinessRepository {
    suspend fun apply(body: BusinessApplyBody): KtorResponse<BusinessNet>
    suspend fun me(): KtorResponse<BusinessNet>
    suspend fun updatePin(body: UpdatePinBody): KtorResponse<BusinessNet>
    suspend fun createQuest(body: CreateQuestBody): KtorResponse<CreatedQuestNet>
    /** Фото/скан документа заявки. @return публичный URL файла */
    suspend fun uploadDocument(bytes: ByteArray, contentType: String): KtorResponse<String>
}

class BusinessRepositoryImpl(private val remote: BusinessRemoteDataSource) : BusinessRepository {

    override suspend fun apply(body: BusinessApplyBody): KtorResponse<BusinessNet> =
        safeRequest { remote.apply(body) }

    override suspend fun me(): KtorResponse<BusinessNet> = safeRequest { remote.me() }

    override suspend fun updatePin(body: UpdatePinBody): KtorResponse<BusinessNet> =
        safeRequest { remote.updatePin(body) }

    override suspend fun createQuest(body: CreateQuestBody): KtorResponse<CreatedQuestNet> =
        safeRequest { remote.createQuest(body) }

    override suspend fun uploadDocument(bytes: ByteArray, contentType: String): KtorResponse<String> {
        val presigned = when (val r = safeRequest<PresignedNet> { remote.presign(PresignBody(kind = "image", contentType = contentType)) }) {
            is KtorResponse.Success -> r.data
            is KtorResponse.Failure -> return r
        }
        val put = try {
            remote.putFile(presigned.uploadUrl, bytes, contentType)
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!put.status.isSuccess()) return KtorResponse.failure(put)
        val confirm = try {
            remote.confirm(ConfirmBody(mediaId = presigned.mediaId, url = presigned.publicUrl, contentType = contentType))
        } catch (t: Throwable) {
            return KtorResponse.failure(t)
        }
        if (!confirm.status.isSuccess()) return KtorResponse.failure(confirm)
        return KtorResponse.Success(presigned.publicUrl)
    }
}

val businessDataModule = module {
    single { BusinessRemoteDataSource(apiClient = get(), uploadClient = get(org.koin.core.qualifier.named("upload"))) }
    single<BusinessRepository> { BusinessRepositoryImpl(remote = get()) }
}
