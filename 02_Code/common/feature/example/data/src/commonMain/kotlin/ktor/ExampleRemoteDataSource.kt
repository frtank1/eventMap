package com.eventmap.example.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import com.eventmap.core.network.CoreConfig

class ExampleRemoteDataSource(
    private val httpClient: HttpClient,
    private val config: CoreConfig
) {
    suspend fun performLogin(
        username: String,
        password: String
    ): HttpResponse {
        return httpClient.submitForm(
            url = config.authTokenPath,
            formParameters = Parameters.build {
                append("username", username)
                append("password", password)
                append("client_id", config.authClientId)
                append("grant_type", "password")
            }
        )
    }
}
