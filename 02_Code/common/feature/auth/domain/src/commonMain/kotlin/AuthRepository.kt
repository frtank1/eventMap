package com.eventmap.auth

import com.eventmap.auth.models.AuthResult
import com.eventmap.core.network.ktor.KtorResponse

/**
 * Контракт авторизации EventMap (раздел A, см. 05_Docs/API_CONTRACT.md).
 * Все методы возвращают KtorResponse и не кидают исключения.
 */
interface AuthRepository {

    suspend fun register(
        email: String,
        password: String,
        name: String,
    ): KtorResponse<AuthResult>

    suspend fun login(
        email: String,
        password: String,
    ): KtorResponse<AuthResult>

    suspend fun forgotPassword(email: String): KtorResponse<Unit>

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): KtorResponse<Unit>

    suspend fun saveInterests(interestIds: List<String>): KtorResponse<Unit>

    /** Залогинен ли пользователь (есть валидная сессия в TokenStorage). */
    fun isLoggedIn(): Boolean

    /**
     * Проверка сессии НА СЕРВЕРЕ (GET /users/me с bearer): протухший access
     * тихо обновится refresh'ем внутри Ktor Auth; мёртвая пара → хранилище чистится.
     * @return true — сессия жива; false — токены мертвы (нужен новый вход);
     *         null — сеть недоступна/прочее (оффлайн не наказываем — пускаем).
     */
    suspend fun validateSession(): Boolean?
}
