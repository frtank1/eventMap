package com.eventmap.example.ui

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.example.ExampleRepository
import com.eventmap.example.models.TokenResponse

class FakeExampleRepository(
    private val result: KtorResponse<TokenResponse>
) : ExampleRepository {
    var lastLogin: String? = null
    var lastPassword: String? = null

    override suspend fun login(login: String, password: String): KtorResponse<TokenResponse> {
        lastLogin = login
        lastPassword = password
        return result
    }

    override fun isUserLoggedIn(): Boolean = false

    companion object {
        fun success() = FakeExampleRepository(
            KtorResponse.Success(
                TokenResponse(
                    accessToken = "token",
                    expiresIn = 3600,
                    refreshExpiresIn = 7200,
                    refreshToken = "refresh",
                    tokenType = "Bearer",
                    scope = "openid"
                )
            )
        )

        fun failure(message: String) = FakeExampleRepository(
            KtorResponse.Failure(ErrorModel(customMessage = message))
        )
    }
}
