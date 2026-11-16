package com.eventmap.event.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.VenueRepository
import com.eventmap.event.models.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

class VenueRepositoryImpl(private val client: HttpClient) : VenueRepository {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun getBuilding(id: String): KtorResponse<VenueBuilding> {
        if (!validBuildingId(id)) return failure("Некорректный идентификатор здания")
        return try {
            withTimeout(10_000) {
                client.prepareGet("venues/$id") { header(HttpHeaders.CacheControl, "no-cache") }.execute { response ->
                    if (response.status != HttpStatusCode.OK) return@execute failure("Не удалось загрузить план", response.status.value)
                    val limit = 1024 * 1024
                    if (response.contentType()?.withoutParameters() != ContentType.Application.Json ||
                        (response.contentLength() ?: 0) > limit) return@execute failure("Неподдерживаемый формат плана")
                    val bytes = response.bodyAsChannel().readRemaining((limit + 1).toLong()).readByteArray()
                    if (bytes.size > limit) return@execute failure("План превышает допустимый размер")
                    val building = json.decodeFromString<VenueBuilding>(bytes.decodeToString(throwOnInvalidSequence = true))
                    if (building.buildingId != id || !building.isValid()) return@execute failure("План несовместим или повреждён")
                    KtorResponse.Success(building)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) { failure("План не ответил вовремя") }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure("Не удалось загрузить план") }
    }
    override suspend fun getEvents(buildingId: String): KtorResponse<List<EventDetail>> {
        if (!validBuildingId(buildingId)) return failure("Некорректный идентификатор здания")
        return when (val result = safeRequest<List<EventDetail>> { client.get("events") { parameter("buildingId", buildingId) } }) {
            // Defence against an old server ignoring an unknown filter. Never show neighbours or private events as inside.
            is KtorResponse.Success -> KtorResponse.Success(result.data.filter { it.venue?.buildingId == buildingId && it.visibility == "public" })
            is KtorResponse.Failure -> result
        }
    }
    private fun failure(message: String, status: Int? = null) = KtorResponse.Failure(ErrorModel(status=status, customMessage=message))
}
