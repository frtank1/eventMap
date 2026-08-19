package com.eventmap.map.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.koin.dsl.module

// =====================================================================
//  Пульс карты (G1/G2/G9, §8.5) + туман войны (G3)
//  DTO зеркалят server/presentation/dto/Dto.kt (PulseResponse, /map/fog)
// =====================================================================

@Serializable
data class PulseEventNet(val eventId: String, val glow: Float = 0f, val crowd10m: Int = 0, val viewers: Int = 0)

@Serializable
data class AnomalyNet(val id: String, val lat: Double, val lng: Double, val userCount: Int = 0, val startedAtMs: Long = 0)

@Serializable
data class PulseNet(val events: List<PulseEventNet> = emptyList(), val anomalies: List<AnomalyNet> = emptyList())

@Serializable
data class FogNet(val visitedDistrictIds: List<String> = emptyList())

@Serializable
data class PingBody(val lat: Double, val lng: Double)

// Гео-шеринг друзей C10/L5 (V12): именная позиция, видна только друзьям, TTL 15 мин
@Serializable
data class FriendLocationNet(
    val userId: String,
    val name: String = "",
    val lat: Double,
    val lng: Double,
    val agoLabel: String = "",
)

class MapPulseRemoteDataSource(private val httpClient: HttpClient) {
    suspend fun pulse(): HttpResponse = httpClient.get("map/pulse")
    suspend fun fog(): HttpResponse = httpClient.get("map/fog")
    suspend fun ping(lat: Double, lng: Double): HttpResponse =
        httpClient.post("map/ping") {
            contentType(ContentType.Application.Json)
            setBody(PingBody(lat, lng))
        }

    // C10/L5: именной шеринг для друзей (отдельно от анонимного пинга)
    suspend fun shareLocation(lat: Double, lng: Double): HttpResponse =
        httpClient.post("friends/location") {
            contentType(ContentType.Application.Json)
            setBody(PingBody(lat, lng))
        }

    suspend fun stopSharing(): HttpResponse = httpClient.delete("friends/location")

    suspend fun friendLocations(): HttpResponse = httpClient.get("friends/locations")

    suspend fun wave(userId: String): HttpResponse = httpClient.post("friends/$userId/wave")
}

interface MapPulseRepository {
    suspend fun pulse(): KtorResponse<PulseNet>
    suspend fun fog(): KtorResponse<FogNet>
    /** Отправка гео-пинга — ТОЛЬКО при включённом тумблере «аналитика» (I2, приватность §8.5) */
    suspend fun ping(lat: Double, lng: Double): KtorResponse<Unit>

    // C10/L5: именной шеринг (тот же тумблер I2) + позиции друзей + «позвать с собой»
    suspend fun shareLocation(lat: Double, lng: Double): KtorResponse<Unit>
    suspend fun stopSharing(): KtorResponse<Unit>
    suspend fun friendLocations(): KtorResponse<List<FriendLocationNet>>
    suspend fun wave(userId: String): KtorResponse<Unit>
}

class MapPulseRepositoryImpl(private val remote: MapPulseRemoteDataSource) : MapPulseRepository {
    override suspend fun pulse(): KtorResponse<PulseNet> = safeRequest { remote.pulse() }
    override suspend fun fog(): KtorResponse<FogNet> = safeRequest { remote.fog() }
    override suspend fun ping(lat: Double, lng: Double): KtorResponse<Unit> = unit { remote.ping(lat, lng) }

    override suspend fun shareLocation(lat: Double, lng: Double): KtorResponse<Unit> = unit { remote.shareLocation(lat, lng) }
    override suspend fun stopSharing(): KtorResponse<Unit> = unit { remote.stopSharing() }
    override suspend fun friendLocations(): KtorResponse<List<FriendLocationNet>> = safeRequest { remote.friendLocations() }
    override suspend fun wave(userId: String): KtorResponse<Unit> = unit { remote.wave(userId) }

    private suspend fun unit(call: suspend () -> HttpResponse): KtorResponse<Unit> =
        when (val r = safeRequest<Unit> { call() }) {
            is KtorResponse.Success -> KtorResponse.Success(Unit)
            is KtorResponse.Failure -> r
        }
}

val mapPulseModule = module {
    single { MapPulseRemoteDataSource(httpClient = get()) }
    single<MapPulseRepository> { MapPulseRepositoryImpl(remote = get()) }
}
