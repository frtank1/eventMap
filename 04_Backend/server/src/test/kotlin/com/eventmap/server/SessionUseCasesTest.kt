package com.eventmap.server

import com.eventmap.server.config.AppConfig
import com.eventmap.server.domain.usecase.IssueTokensUseCase
import com.eventmap.server.domain.usecase.LogoutUseCase
import com.eventmap.server.domain.usecase.RefreshSessionUseCase
import com.eventmap.server.security.JwtService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionUseCasesTest {

    private val jwt = JwtService(AppConfig())

    private fun setup(): Triple<IssueTokensUseCase, RefreshSessionUseCase, FakeRefreshTokenRepository> {
        val repo = FakeRefreshTokenRepository()
        val issue = IssueTokensUseCase(jwt, repo)
        return Triple(issue, RefreshSessionUseCase(repo, issue), repo)
    }

    @Test
    fun `выпуск токенов сохраняет refresh в хранилище`() = runTest {
        val (issue, _, repo) = setup()
        val tokens = issue("user-1")
        assertTrue(tokens.accessToken.isNotBlank())
        assertEquals(1, repo.records.size)
        assertEquals("user-1", repo.records.values.single().userId)
    }

    @Test
    fun `refresh ротирует токен - старый отзывается, выдаётся новый`() = runTest {
        val (issue, refresh, repo) = setup()
        val first = issue("user-1")

        val second = refresh(first.refreshToken)

        assertNotNull(second)
        assertNotEquals(first.refreshToken, second.refreshToken)
        // повторное использование старого refresh — отказ (защита от кражи)
        assertNull(refresh(first.refreshToken))
        assertEquals(2, repo.records.size)
    }

    @Test
    fun `неизвестный refresh-токен - отказ`() = runTest {
        val (_, refresh, _) = setup()
        assertNull(refresh("garbage-token"))
    }

    @Test
    fun `протухший refresh-токен - отказ`() = runTest {
        val repo = FakeRefreshTokenRepository()
        val issue = IssueTokensUseCase(jwt, repo)
        val refresh = RefreshSessionUseCase(repo, issue)
        val tokens = issue("user-1")
        // вручную просрочиваем запись
        val hash = repo.records.keys.single()
        repo.records[hash] = repo.records[hash]!!.copy(expiresAtMs = System.currentTimeMillis() - 1)

        assertNull(refresh(tokens.refreshToken))
    }

    @Test
    fun `logout отзывает refresh-токен`() = runTest {
        val (issue, refresh, repo) = setup()
        val tokens = issue("user-1")

        LogoutUseCase(repo)(tokens.refreshToken)

        assertNull(refresh(tokens.refreshToken))
        assertTrue(repo.records.values.all { it.revoked })
    }
}
