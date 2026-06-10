package com.eventmap.search.data

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.safeRequest
import com.eventmap.search.SearchRepository
import com.eventmap.search.data.ktor.SearchRemoteDataSource
import com.eventmap.search.models.SearchHit

class SearchRepositoryImpl(
    private val remote: SearchRemoteDataSource,
) : SearchRepository {
    override suspend fun search(query: String, type: String): KtorResponse<List<SearchHit>> =
        safeRequest { remote.search(query, type) }
}
