package com.eventmap.core.network

// Всё проектное, что раньше было захардкожено в KtorModule.
// Передаётся из приложения при PlatformSDK.init — ядро остаётся нейтральным.
data class CoreConfig(
    val baseUrl: String,
    // пути, на которые НЕ вешать bearer-токен (логин, регистрация, refresh и т.п.)
    val authExcludedPaths: List<String> = emptyList(),
    // OAuth client_id, если бэкенд его требует (для EventMap не нужен — пусто)
    val authClientId: String = "",
    // эндпоинт обновления токена (для EventMap: "auth/refresh")
    val authTokenPath: String = "",
)
