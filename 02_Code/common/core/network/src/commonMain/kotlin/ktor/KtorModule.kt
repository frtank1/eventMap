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
import com.eventmap.core.datastore.TokenStorage
import com.eventmap.core.network.CoreConfig
import com.eventmap.core.network.error.GlobalError
import com.eventmap.core.network.error.GlobalErrorBus
import com.eventmap.core.network.ktor.HttpEngineFactory
import com.eventmap.core.network.models.TokenRefreshRequest
import com.eventmap.core.network.models.TokenRefreshResponse
import org.koin.dsl.module

val ktorModule = module {

    single {
        val errorBus = get<GlobalErrorBus>()
        val config = get<CoreConfig>()
        val tokenStorage = get<TokenStorage>()

        HttpClient(HttpEngineFactory().createEngine()) {

            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.ALL
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
                    }
                )
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 15_000
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
                        if (session == null || session.refreshToken.isBlank()) {
                            tokenStorage.logout()
                            errorBus.events.emit(GlobalError.Unauthorized)
                            return@refreshTokens null
                        }

                        val response = client.post(config.authTokenPath) {
                            contentType(ContentType.Application.Json)
                            setBody(TokenRefreshRequest(refreshToken = session.refreshToken))
                            markAsRefreshTokenRequest()
                        }

                        if (response.status.isSuccess()) {
                            val new: TokenRefreshResponse = response.body()
                            val refresh = new.refreshToken.ifBlank { session.refreshToken }
                            tokenStorage.saveTokens(
                                userType = tokenStorage.getActiveUser(),
                                access = new.accessToken,
                                refresh = refresh,
                            )
                            BearerTokens(new.accessToken, refresh)
                        } else {
                            tokenStorage.logout()
                            errorBus.events.emit(GlobalError.Unauthorized)
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
