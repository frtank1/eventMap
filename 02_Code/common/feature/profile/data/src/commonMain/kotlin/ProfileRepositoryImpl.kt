package com.eventmap.profile.data

import com.eventmap.core.datastore.TokenStorage
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.profile.ProfileRepository
import com.eventmap.profile.data.ktor.ProfileRemoteDataSource
import com.eventmap.profile.data.ktor.UpdateMeRequest
import com.eventmap.profile.models.HistoryItem
import com.eventmap.profile.models.UserProfile

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val tokenStorage: TokenStorage,
) : ProfileRepository {
    override suspend fun getMe(): KtorResponse<UserProfile> = safeRequest { remote.getMe() }
    override suspend fun getHistory(userId: String): KtorResponse<List<HistoryItem>> =
        safeRequest { remote.getHistory(userId) }
    override suspend fun updateMe(name: String, bio: String): KtorResponse<UserProfile> =
        safeRequest { remote.updateMe(UpdateMeRequest(displayName = name, bio = bio)) }
    override fun logout() = tokenStorage.logout()
}
