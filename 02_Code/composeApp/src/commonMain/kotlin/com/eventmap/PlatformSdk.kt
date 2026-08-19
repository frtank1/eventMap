package com.eventmap

import PlatformConfiguration
import com.eventmap.core.datastore.storageModule
import com.eventmap.core.datastore.tokenStorageModule
import com.eventmap.core.files.filesModule
import com.eventmap.core.files.platformFilesModule
import com.eventmap.core.network.CoreConfig
import com.eventmap.core.network.coreModule
import com.eventmap.core.biometry.biometryModule
import com.eventmap.core.connectivity.connectivityModule
import com.eventmap.core.logger.loggerModule
import com.eventmap.example.data.exampleRepositoryModule
import com.eventmap.example.ui.exampleModule
import com.eventmap.applock.appLockModule
import com.eventmap.auth.data.authRepositoryModule
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.authModule
import com.eventmap.map.screen.mapModule
import com.eventmap.event.data.eventRepositoryModule
import com.eventmap.event.ui.eventModule
import com.eventmap.profile.data.profileRepositoryModule
import com.eventmap.profile.ui.profileModule
import com.eventmap.feed.data.feedRepositoryModule
import com.eventmap.feed.ui.feedModule
import com.eventmap.search.data.searchRepositoryModule
import com.eventmap.search.ui.searchModule
import com.eventmap.video.data.videoRepositoryModule
import com.eventmap.video.ui.videoModule
import com.eventmap.quest.ui.questModule
import com.eventmap.game.ui.gameModule
import com.eventmap.business.ui.businessModule
import com.eventmap.core.platform.geo.geoModule
import com.eventmap.core.platform.reminders.remindersModule
import com.eventmap.core.platform.share.shareModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Дефолт проекта. При форке/новом проекте меняешь тут (или передаёшь свой в init).
// Бэкенд EventMap: Ktor + JWT, эндпоинты /auth/* (см. 05_Docs/API_CONTRACT.md).
private val defaultCoreConfig = CoreConfig(
    // ВАЖНО: без /v1 — сервер отдаёт роуты с корня (/auth, /events). Слэш в конце обязателен.
    // Боевой бэкенд на Render (задеплоен 20.06.2026). Слэш в конце обязателен, без /v1.
    // Для локального бэка: Android-эмулятор → "http://10.0.2.2:8080/", реальный телефон → "http://<IP-Mac>:8080/".
    baseUrl = "https://eventmap-guo1.onrender.com/",
    // на эти пути bearer-токен не вешаем
    authExcludedPaths = listOf(
        "/auth/login",
        "/auth/register",
        "/auth/refresh",
        "/auth/password",
    ),
    authClientId = "", // у EventMap JWT-бэкенд без OAuth client_id
    authTokenPath = "auth/refresh",
    // Пока боевой бэкенд не задеплоен — демо-данные и сетевые логи включены.
    // Перед релизом: demoMode = false, debug = false.
    demoMode = false,
    // debug=true теперь логирует только заголовки/URL/статус (LogLevel.HEADERS), БЕЗ тел и паролей.
    // Включено для отладки регистрации; перед релизом — false.
    debug = true,
)

fun platformModule(configuration: PlatformConfiguration) = module {
    single { configuration }
}

object PlatformSDK {

    fun init(
        configuration: PlatformConfiguration,
        coreConfig: CoreConfig = defaultCoreConfig,
    ) {
        startKoin {
            modules(
                platformModule(configuration),
                module { single { coreConfig } },
                tokenStorageModule,
                storageModule,
                coreModule,
                filesModule,
                platformFilesModule,
                biometryModule,
                geoModule, // геолокация (гео-чекины Q3, «я тоже здесь», пинг карты)
                shareModule, // системный шаринг текста (рилс D8)
                remindersModule, // локальные напоминания «за час до события»
                loggerModule,
                connectivityModule,
                exampleRepositoryModule,
                exampleModule,
                authRepositoryModule,
                authModule,
                appLockModule,
                mapModule,
                eventRepositoryModule,
                eventModule,
                profileRepositoryModule,
                profileModule,
                feedRepositoryModule,
                feedModule,
                searchRepositoryModule,
                searchModule,
                videoRepositoryModule,
                videoModule,
                questModule, // квесты Q1–Q4 → /quests/* (01.07.2026)
                gameModule,  // игровой хаб R1 → /game/* (01.07.2026)
                businessModule, // бизнес M: заявка/статус + конструктор квестов (07.07.2026)
                // Главный экран после авторизации — app shell с картой (раздел C).
                module { single<HomeScreenProvider> { HomeScreenProvider { MainScreen() } } },
            )
        }
    }
}
