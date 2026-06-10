package com.eventmap.example.ui

import kotlinx.coroutines.test.runTest
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.example.ui.domain.LoginUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginUseCaseTest {

    @Test
    fun blankInput_returnsFailure_withoutHittingRepository() = runTest {
        val repo = FakeExampleRepository.success()
        val useCase = LoginUseCase(repo)

        val result = useCase("", "")

        assertIs<KtorResponse.Failure>(result)
        assertTrue(repo.lastLogin == null) // запрос не пошёл
    }

    @Test
    fun validInput_passesTrimmedLogin_andReturnsSuccess() = runTest {
        val repo = FakeExampleRepository.success()
        val useCase = LoginUseCase(repo)

        val result = useCase("  user  ", "pass")

        assertIs<KtorResponse.Success<*>>(result)
        assertEquals("user", repo.lastLogin)
    }

    @Test
    fun repositoryFailure_isPropagated() = runTest {
        val repo = FakeExampleRepository.failure("server down")
        val useCase = LoginUseCase(repo)

        val result = useCase("user", "pass")

        assertIs<KtorResponse.Failure>(result)
    }
}
