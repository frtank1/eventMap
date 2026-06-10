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
import org.koin.core.context.startKoin
import org.koin.dsl.module

// Дефолт проекта. При форке/новом проекте меняешь тут (или передаёшь свой в init).
// Бэкенд EventMap: Ktor + JWT, эндпоинты /auth/* (см. 05_Docs/API_CONTRACT.md).
private val defaultCoreConfig = CoreConfig(
    baseUrl = "https://api.eventmap.app/v1/",
    // на эти пути bearer-токен не вешаем
    authExcludedPaths = listOf(
        "/auth/login",
        "/auth/register",
        "/auth/refresh",
        "/auth/password",
    ),
    authClientId = "", // у EventMap JWT-бэкенд без OAuth client_id
    authTokenPath = "auth/refresh",
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
                loggerModule,
                connectivityModule,
                exampleRepositoryModule,
                exampleModule,
                authRepositoryModule,
                authModule,
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
                // Главный экран после авторизации — app shell с картой (раздел C).
                module { single<HomeScreenProvider> { HomeScreenProvider { MainScreen() } } },
            )
        }
    }
}
