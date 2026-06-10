package com.eventmap.search.data.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

class SearchRemoteDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun search(query: String, type: String): HttpResponse =
        httpClient.get("search") {
            parameter("q", query)
            parameter("type", type)
        }
}
