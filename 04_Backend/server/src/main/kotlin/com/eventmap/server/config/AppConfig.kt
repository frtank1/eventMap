package com.eventmap.server.config

// Конфиг из переменных окружения (с дефолтами для локалки через docker-compose).
data class AppConfig(
    val appEnv: String = env("APP_ENV", "dev"), // dev | prod
    val dbUrl: String = env("DB_URL", "jdbc:postgresql://localhost:5432/eventmap"),
    val dbUser: String = env("DB_USER", "eventmap"),
    val dbPassword: String = env("DB_PASSWORD", "eventmap"),
    val jwtSecret: String = env("JWT_SECRET", DEV_SECRET),
    val jwtIssuer: String = env("JWT_ISSUER", "eventmap"),
    val jwtAudience: String = env("JWT_AUDIENCE", "eventmap-app"),
    val port: Int = env("PORT", "8080").toInt(),
    // Разрешённые CORS-хосты через запятую (например "app.eventmap.kz,eventmap.kz").
    // Пусто = anyHost — допустимо только в dev.
    val corsHosts: List<String> = env("CORS_HOSTS", "")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
) {
    init {
        require(!(appEnv == "prod" && jwtSecret == DEV_SECRET)) {
            "JWT_SECRET обязателен в prod — dev-дефолт запрещён"
        }
        require(!(appEnv == "prod" && corsHosts.isEmpty())) {
            "CORS_HOSTS обязателен в prod — anyHost запрещён"
        }
    }

    companion object {
        private const val DEV_SECRET = "dev-secret-change-me"
        private fun env(key: String, default: String) = System.getenv(key) ?: default
    }
}
