package com.eventmap.example.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.example.ExampleRepository
import com.eventmap.example.models.TokenResponse

class ExampleRepositoryImpl(
    private val remoteDataSource: ExampleRemoteDataSource
) : ExampleRepository {

    override suspend fun login(
        login: String,
        password: String
    ): KtorResponse<TokenResponse> {
        return safeRequest {
            remoteDataSource.performLogin(username = login, password = password)
        }
    }

    override fun isUserLoggedIn(): Boolean = false
}
