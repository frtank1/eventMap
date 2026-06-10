package com.eventmap.event.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class RatingRequest(val value: Int, val review: String)

class EventRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getEvents(): HttpResponse = httpClient.get("events")

    suspend fun getEvent(id: String): HttpResponse = httpClient.get("events/$id")

    suspend fun getParticipants(id: String, type: String): HttpResponse =
        httpClient.get("events/$id/participants") { parameter("type", type) }

    suspend fun getMedia(id: String): HttpResponse = httpClient.get("events/$id/media")

    suspend fun attend(id: String): HttpResponse = httpClient.post("events/$id/attend")

    suspend fun createEvent(body: CreateEventRequest): HttpResponse =
        httpClient.post("events") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun purchase(id: String): HttpResponse = httpClient.post("events/$id/purchase")

    suspend fun getTicket(id: String): HttpResponse = httpClient.get("events/$id/ticket")

    suspend fun getRatings(id: String): HttpResponse = httpClient.get("events/$id/ratings")

    suspend fun rateEvent(id: String, body: RatingRequest): HttpResponse =
        httpClient.post("events/$id/rating") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun getOrganizer(id: String): HttpResponse = httpClient.get("organizers/$id")

    suspend fun getOrganizerEvents(id: String): HttpResponse = httpClient.get("organizers/$id/events")

    suspend fun subscribe(organizerId: String): HttpResponse = httpClient.post("organizers/$organizerId/subscribe")

    suspend fun unsubscribe(organizerId: String): HttpResponse = httpClient.delete("organizers/$organizerId/subscribe")
}
