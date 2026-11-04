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

    // Публичный базовый URL сервиса — для ссылок фолбэк-хранилища медиа (V10).
    // Render подставляет RENDER_EXTERNAL_URL автоматически; локально — localhost.
    val publicBaseUrl: String = env("PUBLIC_BASE_URL", env("RENDER_EXTERNAL_URL", "http://localhost:8080")),

    // Object storage (Cloudflare R2 / MinIO) для фото и видео. Пусто = включается
    // фолбэк-хранилище в Postgres (data/storage/DbStorageService.kt) — ок для теста/демо;
    // прод — R2. См. 05_Docs/STORAGE_SETUP.md и data/storage/StorageService.kt.
    val s3Endpoint: String = env("S3_ENDPOINT", ""),    // https://<account>.r2.cloudflarestorage.com
    val s3Region: String = env("S3_REGION", "auto"),    // R2: "auto"
    val s3Bucket: String = env("S3_BUCKET", ""),
    val s3AccessKey: String = env("S3_ACCESS_KEY", ""),
    val s3SecretKey: String = env("S3_SECRET_KEY", ""),
    val s3PublicBase: String = env("S3_PUBLIC_BASE", ""), // публичный CDN-домен бакета (опц.)

    val videoProvider: String = env("VIDEO_PROVIDER", "storage"),
    val muxTokenId: String = env("MUX_TOKEN_ID", ""),
    val muxTokenSecret: String = env("MUX_TOKEN_SECRET", ""),
    val muxWebhookSecret: String = env("MUX_WEBHOOK_SECRET", ""),
    val muxSigningKeyId: String = env("MUX_SIGNING_KEY_ID", ""),
    val muxSigningPrivateKey: String = env("MUX_SIGNING_PRIVATE_KEY", ""),
    val muxMaxUploadsPerDay: Int = env("MUX_MAX_UPLOADS_PER_DAY", "20").toInt(),
    val muxMaxDurationSeconds: Int = env("MUX_MAX_DURATION_SECONDS", "120").toInt(),

    // Push (FCM HTTP v1). Из service-account JSON Firebase: project_id, client_email, private_key.
    // Пусто = пуши выключены (отправка — no-op). private_key с \n-экранированием — нормализуется.
    val fcmProjectId: String = env("FCM_PROJECT_ID", ""),
    val fcmClientEmail: String = env("FCM_CLIENT_EMAIL", ""),
    val fcmPrivateKey: String = env("FCM_PRIVATE_KEY", ""),
) {
    init {
        require(videoProvider in setOf("storage", "mux")) { "VIDEO_PROVIDER: storage | mux" }
        require(muxMaxUploadsPerDay in 1..1000 && muxMaxDurationSeconds in 1..600)
        require(!(appEnv == "prod" && jwtSecret == DEV_SECRET)) {
            "JWT_SECRET обязателен в prod — dev-дефолт запрещён"
        }
        require(!(appEnv == "prod" && corsHosts.isEmpty())) {
            "CORS_HOSTS обязателен в prod — anyHost запрещён"
        }
        require(!(appEnv == "prod" && adminKey == DEV_ADMIN_KEY)) {
            "ADMIN_KEY обязателен в prod — dev-дефолт запрещён"
        }
        // Фолбэк-хранилище медиа строит абсолютные ссылки: localhost в prod = битые URL
        require(!(appEnv == "prod" && s3Endpoint.isBlank() && publicBaseUrl.startsWith("http://localhost"))) {
            "PUBLIC_BASE_URL (или S3_*) обязателен в prod — иначе ссылки на медиа будут битыми"
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
