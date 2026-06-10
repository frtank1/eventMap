package com.eventmap.auth.data

import com.eventmap.auth.AuthRepository
import com.eventmap.auth.data.ktor.AuthRemoteDataSource
import com.eventmap.auth.data.ktor.ForgotRequest
import com.eventmap.auth.data.ktor.InterestsRequest
import com.eventmap.auth.data.ktor.LoginRequest
import com.eventmap.auth.data.ktor.RegisterRequest
import com.eventmap.auth.data.ktor.ResetRequest
import com.eventmap.auth.models.AuthResult
import com.eventmap.core.datastore.TokenStorage
import com.eventmap.core.datastore.UserType
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val tokenStorage: TokenStorage,
) : AuthRepository {

    override suspend fun register(
        email: String,
        password: String,
        name: String,
    ): KtorResponse<AuthResult> =
        safeRequest<AuthResult> {
            remote.register(RegisterRequest(email, password, name))
        }.also { it.persistTokens() }
            .orDevBypass(name.ifBlank { email.substringBefore("@") })

    override suspend fun login(
        email: String,
        password: String,
    ): KtorResponse<AuthResult> =
        safeRequest<AuthResult> {
            remote.login(LoginRequest(email, password))
        }.also { it.persistTokens() }
            .orDevBypass(email.substringBefore("@"))

    override suspend fun forgotPassword(email: String): KtorResponse<Unit> =
        unitRequest { remote.forgotPassword(ForgotRequest(email)) }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): KtorResponse<Unit> =
        unitRequest { remote.resetPassword(ResetRequest(email, code, newPassword)) }

    override suspend fun saveInterests(interestIds: List<String>): KtorResponse<Unit> =
        unitRequest { remote.saveInterests(InterestsRequest(interestIds)) }

    override fun isLoggedIn(): Boolean = tokenStorage.isAuthorized()

    // --- helpers ---

    private fun KtorResponse<AuthResult>.persistTokens() {
        if (this is KtorResponse.Success) {
            tokenStorage.saveTokens(
                userType = UserType.EXTERNAL,
                access = data.accessToken,
                refresh = data.refreshToken,
            )
        }
    }

    // TODO(dev): УБРАТЬ ПЕРЕД ПРОДОМ. Пока бэкенда нет — при сетевой ошибке пускаем
    // внутрь с фейковым токеном, чтобы можно было потыкать приложение любым логином.
    private fun KtorResponse<AuthResult>.orDevBypass(name: String): KtorResponse<AuthResult> {
        if (this is KtorResponse.Failure) {
            val fake = AuthResult(accessToken = "dev-token", refreshToken = "dev-refresh")
            tokenStorage.saveTokens(UserType.EXTERNAL, fake.accessToken, fake.refreshToken)
            return KtorResponse.Success(fake.copy(user = fake.user.copy(name = name)))
        }
        return this
    }

    private suspend inline fun unitRequest(
        call: () -> HttpResponse,
    ): KtorResponse<Unit> = try {
        val r = call()
        if (r.status.isSuccess()) KtorResponse.Success(Unit) else KtorResponse.failure<Unit>(r)
    } catch (t: Throwable) {
        KtorResponse.failure(t)
    }
}
