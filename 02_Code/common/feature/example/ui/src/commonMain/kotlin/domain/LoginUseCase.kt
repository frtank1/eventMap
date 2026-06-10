package com.eventmap.example.ui.domain

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.example.ExampleRepository
import com.eventmap.example.models.TokenResponse

// Тонкий слой между ViewModel и репозиторием: валидация + сам запрос.
class LoginUseCase(
    private val repository: ExampleRepository
) {
    suspend operator fun invoke(
        login: String,
        password: String
    ): KtorResponse<TokenResponse> {
        if (login.isBlank() || password.isBlank()) {
            return KtorResponse.Failure(
                ErrorModel(customMessage = "Заполните логин и пароль")
            )
        }
        return repository.login(login.trim(), password)
    }
}
