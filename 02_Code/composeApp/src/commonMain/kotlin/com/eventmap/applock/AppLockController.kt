package com.eventmap.applock

import com.eventmap.auth.AuthRepository
import com.eventmap.core.biometry.BiometricAuthenticator
import com.eventmap.core.biometry.BiometricResult
import com.eventmap.core.datastore.AppLockStorage
import com.eventmap.core.datastore.TokenStorage
import com.eventmap.core.network.ktor.KtorResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeSource

/** Что показывает оверлей App Lock поверх навигатора. */
enum class AppLockState {
    /** Оверлея нет — приложение доступно. */
    Unlocked,

    /** Экран разблокировки (PIN / биометрия). */
    Locked,

    /** Предложение создать PIN (после входа, если ещё не настроен). */
    NeedsSetup,
}

/** Результат разблокировки. */
enum class UnlockResult {
    /** Сессия была живой — снимаем оверлёр, экран под ним корректен. */
    Success,

    /** Токен был мёртв — сделали тихий ре-логин. Нужно увести на главный экран. */
    Recovered,

    /** Тихий ре-логин невозможен — нужен полный вход по паролю. */
    NeedFullLogin,
}

/**
 * Единый контроллер блокировки приложения (App Lock).
 *
 * Поведение:
 *  - холодный старт с настроенным PIN → [Locked];
 *  - уход в фон → при возврате [Locked] (если PIN настроен);
 *  - 401 / смерть токена ([onUnauthorized]) → [Locked] вместо сброса в логин;
 *  - после успешного PIN/биометрии [completeUnlock] поднимает живую сессию,
 *    а если токен умер — делает ТИХИЙ РЕ-ЛОГИН по сохранённой учётке.
 *
 * Singleton в Koin: одно состояние на весь процесс.
 */
class AppLockController(
    private val appLockStorage: AppLockStorage,
    private val tokenStorage: TokenStorage,
    private val biometric: BiometricAuthenticator,
    private val authRepository: AuthRepository,
) {
    private val _state = MutableStateFlow(AppLockState.Unlocked)
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private val clock = TimeSource.Monotonic
    private var backgroundMark: TimeSource.Monotonic.ValueTimeMark? = null

    // Идёт биометрия/ре-логин — не блокируем по lifecycle (системная биом-шторка
    // на части устройств триггерит фон/форграунд и иначе устроила бы петлю блокировок).
    private var authInProgress = false

    // Предложение настроить PIN показываем один раз за жизнь процесса.
    private var setupOffered = false

    // --- статус ---

    fun isPinSet(): Boolean = appLockStorage.isPinSet()
    fun isBiometricEnabled(): Boolean = appLockStorage.isBiometricEnabled()
    fun isBiometricAvailable(): Boolean = biometric.isAvailable()

    // --- триггеры lifecycle / навигации ---

    /** Холодный старт. Если PIN настроен — стартуем заблокированными. */
    fun onAppStart() {
        _state.value = if (appLockStorage.isPinSet()) AppLockState.Locked else AppLockState.Unlocked
    }

    /** Уход в фон — запоминаем момент. */
    fun onEnterBackground() {
        if (authInProgress) return
        backgroundMark = clock.markNow()
    }

    /** Возврат из фона — блокируем, если прошло не меньше грейса. */
    fun onEnterForeground() {
        if (authInProgress) return
        val mark = backgroundMark
        backgroundMark = null
        if (appLockStorage.isPinSet() && mark != null && mark.elapsedNow() >= LOCK_GRACE) {
            _state.value = AppLockState.Locked
        }
    }

    /** Пользователь дошёл до главного экрана. Если PIN ещё нет — один раз предложить настроить. */
    fun onHomeEntered() {
        if (!appLockStorage.isPinSet() && !setupOffered) {
            setupOffered = true
            _state.value = AppLockState.NeedsSetup
        }
    }

    /**
     * Токен умер (401 и refresh не помог).
     * @return true — показали экран разблокировки; false — PIN не настроен,
     *         вызывающий делает обычный сброс в логин.
     */
    fun onUnauthorized(): Boolean {
        return if (appLockStorage.isPinSet()) {
            _state.value = AppLockState.Locked
            true
        } else {
            false
        }
    }

    // --- разблокировка ---

    fun verifyPin(pin: String): Boolean = appLockStorage.verifyPin(pin)

    /** Биометрия. true — успех. Гость [authInProgress] защищает от петли блокировок. */
    suspend fun authenticateBiometric(): Boolean {
        if (!appLockStorage.isBiometricEnabled() || !biometric.isAvailable()) return false
        authInProgress = true
        return try {
            biometric.authenticate(BIOMETRIC_REASON) is BiometricResult.Success
        } finally {
            authInProgress = false
        }
    }

    /**
     * Завершение разблокировки после успешного PIN/биометрии.
     * Если сессия жива — просто снимаем оверлей. Если токен умер —
     * тихий ре-логин по сохранённой учётке.
     */
    suspend fun completeUnlock(): UnlockResult {
        if (tokenStorage.isAuthorized()) {
            _state.value = AppLockState.Unlocked
            return UnlockResult.Success
        }
        val creds = appLockStorage.credentials() ?: return UnlockResult.NeedFullLogin
        authInProgress = true
        val response = try {
            authRepository.login(creds.email, creds.password)
        } finally {
            authInProgress = false
        }
        return if (response is KtorResponse.Success) {
            _state.value = AppLockState.Unlocked
            UnlockResult.Recovered
        } else {
            UnlockResult.NeedFullLogin
        }
    }

    // --- настройка ---

    fun configurePin(pin: String) = appLockStorage.setPin(pin)

    fun enableBiometric(enabled: Boolean) = appLockStorage.setBiometricEnabled(enabled)

    /** Закрыть предложение настройки (создал PIN или «Позже»). */
    fun dismissSetup() {
        _state.value = AppLockState.Unlocked
    }

    // --- логаут ---

    /** Реальный выход из аккаунта: стираем PIN, биометрию, учётку и токены. */
    fun onLogout() {
        appLockStorage.clearAll()
        tokenStorage.logout()
        backgroundMark = null
        setupOffered = false
        _state.value = AppLockState.Unlocked
    }

    companion object {
        /** Длина PIN. */
        const val PIN_LENGTH = 4

        // 0 = блокировать сразу при сворачивании. Увеличить, если мешает быстрому переключению.
        private val LOCK_GRACE = kotlin.time.Duration.ZERO

        private const val BIOMETRIC_REASON = "Подтвердите вход в EventMap"
    }
}
