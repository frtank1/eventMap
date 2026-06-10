package com.eventmap.profile

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.profile.models.HistoryItem
import com.eventmap.profile.models.UserProfile

interface ProfileRepository {
    suspend fun getMe(): KtorResponse<UserProfile>
    suspend fun getHistory(userId: String): KtorResponse<List<HistoryItem>>
    suspend fun updateMe(name: String, bio: String): KtorResponse<UserProfile>
    fun logout()
}
