package com.eventmap.core.network.ktor

import com.eventmap.core.network.models.CustomErrorCode
import com.eventmap.core.network.models.ErrorModel
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.statement.HttpResponse
import io.ktor.utils.io.errors.IOException

sealed class KtorResponse<out T> {


    data class Success<T>(
        val data: T
    ) : KtorResponse<T>()

    data class Failure(
        val error: ErrorModel
    ) : KtorResponse<Nothing>()

    companion object {

        suspend inline fun <reified T> success(
            response: HttpResponse
        ): KtorResponse<T> {
            return Success(response.body())
        }

         suspend inline fun <reified T> failure(
            response: HttpResponse
        ): KtorResponse<T> {
            return Failure(parseError(response))
        }

        fun failure(
            throwable: Throwable
        ): KtorResponse<Nothing> {
            return Failure(mapException(throwable))
        }


        private fun mapException(t: Throwable): ErrorModel {
            return when (t) {

                is ClientRequestException -> {
                    // 4xx
                    createErrorModel(
                        statusCode = t.response.status.value,
                        customMessage = t.message
                    )
                }

                is ServerResponseException -> {
                    // 5xx
                    createErrorModel(
                        statusCode = t.response.status.value,
                        customMessage = t.message
                    )
                }

                is ResponseException -> {
                    createErrorModel(
                        statusCode = t.response.status.value,
                        customMessage = t.message
                    )
                }

                is IOException -> {
                    createErrorModel(
                        statusCode = CustomErrorCode.INTERNET_DISMISS,
                        customMessage = "No internet connection"
                    )
                }

                else -> {
                    createErrorModel(
                        statusCode = CustomErrorCode.UNKNOWN_ERROR,
                        customMessage = t.message ?: "Unknown error"
                    )
                }
            }
        }

        suspend fun parseError(
            response: HttpResponse
        ): ErrorModel {
            return try {
                response.body()
            } catch (_: Throwable) {
                createErrorModel(
                    statusCode = response.status.value,
                    customMessage = response.status.description
                )
            }
        }

        private fun createErrorModel(
            statusCode: Int,
            customMessage: String?
        ): ErrorModel {
            return ErrorModel(
                status = statusCode,
                message = null,
                customMessage = customMessage.orEmpty()
            )
        }
    }
}
