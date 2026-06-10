package com.eventmap.feed.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.feed.FeedRepository
import com.eventmap.feed.data.ktor.FeedRemoteDataSource
import com.eventmap.feed.models.FeedItem
import com.eventmap.feed.models.NotificationItem
import com.eventmap.feed.models.SubscriptionItem
import com.eventmap.feed.models.SubscriptionRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class FeedRepositoryImpl(
    private val remote: FeedRemoteDataSource,
) : FeedRepository {
    override suspend fun getFeed(): KtorResponse<List<FeedItem>> = safeRequest { remote.getFeed() }
    override suspend fun getNotifications(): KtorResponse<List<NotificationItem>> = safeRequest { remote.getNotifications() }
    override suspend fun getSubscriptions(tab: String): KtorResponse<List<SubscriptionItem>> = safeRequest { remote.getSubscriptions(tab) }
    override suspend fun getRequests(): KtorResponse<List<SubscriptionRequest>> = safeRequest { remote.getRequests() }
    override suspend fun approveRequest(id: String): KtorResponse<Unit> = unitRequest { remote.approveRequest(id) }
    override suspend fun rejectRequest(id: String): KtorResponse<Unit> = unitRequest { remote.rejectRequest(id) }

    private suspend inline fun unitRequest(call: () -> HttpResponse): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
