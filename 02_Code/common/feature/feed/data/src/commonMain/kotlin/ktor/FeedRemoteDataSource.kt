package com.eventmap.feed.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class FeedRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getFeed(): HttpResponse = httpClient.get("feed")
    suspend fun getNotifications(): HttpResponse = httpClient.get("notifications")
    suspend fun getSubscriptions(tab: String): HttpResponse =
        httpClient.get("subscriptions") { parameter("tab", tab) }
    suspend fun getRequests(): HttpResponse = httpClient.get("subscription-requests")
    suspend fun approveRequest(id: String): HttpResponse = httpClient.post("subscription-requests/$id/approve")
    suspend fun rejectRequest(id: String): HttpResponse = httpClient.post("subscription-requests/$id/reject")
}
