package com.eventmap.server.config

// Конфиг из переменных окружения (с дефолтами для локалки через docker-compose).
data class AppConfig(
    val appEnv: String = env("APP_ENV", "dev"), // dev | prod
    // Приоритет: явные DB_URL/DB_USER/DB_PASSWORD > DATABASE_URL (стандарт хостингов) > локалка.
    val dbUrl: String = env("DB_URL", PG.jdbcUrl),
    val dbUser: String = env("DB_USER", PG.user),
    val dbPassword: String = env("DB_PASSWORD", PG.password),
    val jwtSecret: String = env("JWT_SECRET", DEV_SECRET),
    val jwtIssuer: String = env("JWT_ISSUER", "eventmap"),
    val jwtAudience: String = env("JWT_AUDIENCE", "eventmap-app"),
    val port: Int = env("PORT", "8080").toInt(),
    // Админ-операции (модерация бизнеса/квестов, наборы анимаций) + HMAC QR-кодов чеков.
    val adminKey: String = env("ADMIN_KEY", DEV_ADMIN_KEY),
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
        require(!(appEnv == "prod" && adminKey == DEV_ADMIN_KEY)) {
            "ADMIN_KEY обязателен в prod — dev-дефолт запрещён"
        }
    }

    companion object {
        private const val DEV_SECRET = "dev-secret-change-me"
        private const val DEV_ADMIN_KEY = "dev-admin-change-me"
        private fun env(key: String, default: String) = System.getenv(key) ?: default

        // Render/Railway/Heroku/Fly выдают одну переменную DATABASE_URL в форме
        // postgres://user:pass@host:port/db — разбираем её в jdbc + user + password.
        private val PG = parseDatabaseUrl(System.getenv("DATABASE_URL"))

        private data class Pg(val jdbcUrl: String, val user: String, val password: String)

        private fun parseDatabaseUrl(raw: String?): Pg {
            if (raw.isNullOrBlank()) {
                return Pg("jdbc:postgresql://localhost:5432/eventmap", "eventmap", "eventmap")
            }
            val uri = java.net.URI(raw)
            val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
            val port = if (uri.port != -1) uri.port else 5432
            val db = uri.path.trimStart('/')
            val query = uri.query?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
            return Pg(
                jdbcUrl = "jdbc:postgresql://${uri.host}:$port/$db$query",
                user = userInfo.getOrNull(0).orEmpty(),
                password = userInfo.getOrNull(1).orEmpty(),
            )
        }
    }
}
