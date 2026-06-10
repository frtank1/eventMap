package com.eventmap.core.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorModel(
    val status: Int? = null,

    val code: String? = null,

    val message: String? = null,

    val customMessage: String? = null,

    @SerialName("error")
    val error: String? = null,

    @SerialName("error_description")
    val errorDescription: String? = null
)