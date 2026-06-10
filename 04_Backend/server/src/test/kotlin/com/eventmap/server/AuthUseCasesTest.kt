package com.eventmap.server

import com.eventmap.server.domain.usecase.LoginUseCase
import com.eventmap.server.domain.usecase.RegisterUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthUseCasesTest {

    private fun usecases(): Pair<RegisterUseCase, LoginUseCase> {
        val users = FakeUserRepository()
        return RegisterUseCase(users) to LoginUseCase(users)
    }

    @Test
    fun `регистрация с некорректным email - ошибка`() = runTest {
        val (register, _) = usecases()
        assertIs<RegisterUseCase.Result.Error>(register("not-an-email", "secret123", "Имя"))
    }

    @Test
    fun `регистрация с коротким паролем - ошибка`() = runTest {
        val (register, _) = usecases()
        assertIs<RegisterUseCase.Result.Error>(register("a@b.kz", "12345", "Имя"))
    }

    @Test
    fun `повторная регистрация на тот же email - ошибка`() = runTest {
        val (register, _) = usecases()
        assertIs<RegisterUseCase.Result.Ok>(register("a@b.kz", "secret123", "Имя"))
        assertIs<RegisterUseCase.Result.Error>(register("a@b.kz", "secret123", "Имя"))
    }

    @Test
    fun `пустое имя - подставляется из email`() = runTest {
        val (register, _) = usecases()
        val r = register("azamat@b.kz", "secret123", "")
        assertIs<RegisterUseCase.Result.Ok>(r)
        assertEquals("azamat", r.user.name)
    }

    @Test
    fun `после регистрации логин проходит, пароль хешируется`() = runTest {
        val (register, login) = usecases()
        val r = register("a@b.kz", "secret123", "Имя")
        assertIs<RegisterUseCase.Result.Ok>(r)
        // в хранилище не plaintext
        assertEquals(false, r.user.passwordHash == "secret123")
        assertNotNull(login("a@b.kz", "secret123"))
    }

    @Test
    fun `логин с неверным паролем - null`() = runTest {
        val (register, login) = usecases()
        register("a@b.kz", "secret123", "Имя")
        assertNull(login("a@b.kz", "wrong-pass"))
        assertNull(login("nobody@b.kz", "secret123"))
    }
}
