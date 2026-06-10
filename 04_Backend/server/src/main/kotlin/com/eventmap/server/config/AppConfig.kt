package com.eventmap.server.config

// Конфиг из переменных окружения (с дефолтами для локалки через docker-compose).
data class AppConfig(
    val dbUrl: String = env("DB_URL", "jdbc:postgresql://localhost:5432/eventmap"),
    val dbUser: String = env("DB_USER", "eventmap"),
    val dbPassword: String = env("DB_PASSWORD", "eventmap"),
    val jwtSecret: String = env("JWT_SECRET", "dev-secret-change-me"),
    val jwtIssuer: String = env("JWT_ISSUER", "eventmap"),
    val jwtAudience: String = env("JWT_AUDIENCE", "eventmap-app"),
    val port: Int = env("PORT", "8080").toInt(),
) {
    companion object {
        private fun env(key: String, default: String) = System.getenv(key) ?: default
    }
}
