package com.eventmap.search

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.search.models.SearchHit

interface SearchRepository {
    suspend fun search(query: String, type: String): KtorResponse<List<SearchHit>>
}
