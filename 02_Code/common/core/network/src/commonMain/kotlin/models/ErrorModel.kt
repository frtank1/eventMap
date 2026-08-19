package com.eventmap.core.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorModel(
    val status: Int? = null,

    val code: String? = null,

    val message: String? = null,

    val customMessage: String? = null,

    // Сервер шлёт ошибку объектом: {"error": {"code": "...", "message": "..."}}
    @SerialName("error")
    val error: ErrorBody? = null,

    @SerialName("error_description")
    val errorDescription: String? = null
) {
    @Serializable
    data class ErrorBody(
        val code: String? = null,
        val message: String? = null,
    )
}