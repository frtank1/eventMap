package com.eventmap.example

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.example.models.TokenResponse

interface ExampleRepository {
    suspend fun login(login: String, password: String): KtorResponse<TokenResponse>
    fun isUserLoggedIn(): Boolean
}
