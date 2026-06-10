package com.eventmap.feed

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.feed.models.FeedItem
import com.eventmap.feed.models.NotificationItem
import com.eventmap.feed.models.SubscriptionItem
import com.eventmap.feed.models.SubscriptionRequest

interface FeedRepository {
    suspend fun getFeed(): KtorResponse<List<FeedItem>>
    suspend fun getNotifications(): KtorResponse<List<NotificationItem>>
    suspend fun getSubscriptions(tab: String): KtorResponse<List<SubscriptionItem>>
    suspend fun getRequests(): KtorResponse<List<SubscriptionRequest>>
    suspend fun approveRequest(id: String): KtorResponse<Unit>
    suspend fun rejectRequest(id: String): KtorResponse<Unit>
}
