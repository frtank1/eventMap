import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import com.eventmap.core.datastore.TokenStorage
import com.eventmap.core.network.CoreConfig
import com.eventmap.core.network.auth.Jwt
import com.eventmap.core.network.error.GlobalError
import com.eventmap.core.network.error.GlobalErrorBus
import com.eventmap.core.network.ktor.HttpEngineFactory
import com.eventmap.core.network.models.TokenRefreshRequest
import com.eventmap.core.network.models.TokenRefreshResponse
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal fun refreshRejectsSession(status: Int): Boolean = status == 401 || status == 403

val ktorModule = module {

    // «Голый» клиент для прямого PUT файла в object storage (R2/S3).
    // Без Auth-плагина: bearer на presigned-URL не входит в подпись SigV4 и ломает запрос.
    single(named("upload")) {
        HttpClient(HttpEngineFactory().createEngine()) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 120_000  // запас под видео ~30 МБ
                socketTimeoutMillis = 120_000
            }
        }
    }

    single {
        val errorBus = get<GlobalErrorBus>()
        val config = get<CoreConfig>()
        val tokenStorage = get<TokenStorage>()

        HttpClient(HttpEngineFactory().createEngine()) {

            // HEADERS — метод/URL/статус/заголовки, но БЕЗ тел запросов (без паролей/токенов в логе).
            // Достаточно, чтобы видеть, ушёл ли запрос и что вернул, не светя секреты.
            install(Logging) {
                logger = Logger.SIMPLE
                level = if (config.debug) LogLevel.HEADERS else LogLevel.NONE
            }

            install(DefaultRequest) {
                url(config.baseUrl)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        prettyPrint = true
                        // ВАЖНО: отправлять и поля со значениями по умолчанию.
                        // Иначе price=EventPrice("free") сериализуется как {} (всё дефолтное),
                        // а серверный PriceDto.type обязателен → 500 при создании события.
                        encodeDefaults = true
                    }
                )
            }

            install(HttpTimeout) {
                // Render free-план засыпает после 15 мин простоя и просыпается ~30 сек
                // (холодный старт). Прежние 4 сек на connect убивали первый «будящий»
                // запрос → создание/логин висли и падали. Даём серверу проснуться.
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 30_000
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStorage.getActiveSession()?.let {
                            BearerTokens(it.accessToken, it.refreshToken)
                        }
                    }

                    // На 401 Ktor сам зовёт refreshTokens, обновляет токен и повторяет запрос.
                    refreshTokens {
                        val session = tokenStorage.getActiveSession()

                        // Сессии нет или refresh-токен сам просрочен → он мёртв, сервер не спасёт.
                        // Сбрасываем на авторизацию (App слушает Unauthorized → SplashScreen).
                        if (session == null || session.refreshToken.isBlank() ||
                            Jwt.isExpired(session.refreshToken)
                        ) {
                            tokenStorage.logout()
                            errorBus.events.emit(GlobalError.Unauthorized)
                            return@refreshTokens null
                        }

                        // refresh-токен ещё жив → пробуем обновиться. Сервер может спать
                        // (Render cold start) или лежать — тогда это временный сбой,
                        // сессию НЕ убиваем, просто даём запросу упасть.
                        val response = try {
                            client.post(config.authTokenPath) {
                                contentType(ContentType.Application.Json)
                                setBody(TokenRefreshRequest(refreshToken = session.refreshToken))
                                markAsRefreshTokenRequest()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return@refreshTokens null // сеть/таймаут: не разлогиниваем живую сессию
                        }

                        if (response.status.isSuccess()) {
                            val new = try {
                                response.body<TokenRefreshResponse>()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                return@refreshTokens null
                            }
                            if (new.accessToken.isBlank()) return@refreshTokens null
                            val refresh = new.refreshToken.ifBlank { session.refreshToken }
                            tokenStorage.saveTokens(
                                userType = tokenStorage.getActiveUser(),
                                access = new.accessToken,
                                refresh = refresh,
                            )
                            BearerTokens(new.accessToken, refresh)
                        } else {
                            // 429/5xx/неожиданный ответ — временная ошибка, а не отзыв сессии.
                            if (refreshRejectsSession(response.status.value)) {
                                tokenStorage.logout()
                                errorBus.events.emit(GlobalError.Unauthorized)
                            }
                            null
                        }
                    }

                    sendWithoutRequest { request ->
                        val path = request.url.encodedPath
                        // токен НЕ шлём на исключённые пути (логин, refresh и т.п.)
                        config.authExcludedPaths.none { path.contains(it) }
                    }
                }
            }
        }
    }
}
