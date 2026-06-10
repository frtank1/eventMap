package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.User
import com.eventmap.server.domain.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt

class RegisterUseCase(private val users: UserRepository) {
    sealed interface Result {
        data class Ok(val user: User) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(email: String, password: String, name: String): Result {
        if (!email.contains("@")) return Result.Error("Некорректный email")
        if (password.length < 6) return Result.Error("Пароль минимум 6 символов")
        if (users.byEmail(email) != null) return Result.Error("Пользователь уже существует")
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        return Result.Ok(users.create(email.trim(), name.ifBlank { email.substringBefore("@") }, hash))
    }
}

class LoginUseCase(private val users: UserRepository) {
    suspend operator fun invoke(email: String, password: String): User? {
        val user = users.byEmail(email.trim()) ?: return null
        return if (BCrypt.checkpw(password, user.passwordHash)) user else null
    }
}
