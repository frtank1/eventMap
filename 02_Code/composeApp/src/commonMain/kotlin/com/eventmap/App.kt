package com.eventmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.eventmap.applock.AppLockController
import com.eventmap.applock.AppLockState
import com.eventmap.applock.LockScreen
import com.eventmap.applock.PinSetupScreen
import com.eventmap.core.network.error.GlobalError
import com.eventmap.core.network.error.GlobalErrorBus
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.screens.ForgotPasswordScreen
import com.eventmap.auth.ui.screens.LoginScreen
import com.eventmap.auth.ui.screens.RegisterScreen
import com.eventmap.auth.ui.screens.SplashScreen
import com.eventmap.auth.ui.screens.WelcomeScreen
import com.eventmap.theme.EventMapTheme
import org.koin.mp.KoinPlatform.getKoin

@Composable
@Preview
fun App() {
    val errorBus = remember { getKoin().get<GlobalErrorBus>() }
    val appLock = remember { getKoin().get<AppLockController>() }
    val homeProvider = remember { getKoin().get<HomeScreenProvider>() }
    val globalSnackbar = remember { SnackbarHostState() }
    val navigator = remember { mutableStateOf<Navigator?>(null) }

    val lockState by appLock.state.collectAsState()

    // Холодный старт: если PIN настроен — стартуем заблокированными.
    LaunchedEffect(Unit) { appLock.onAppStart() }

    // Блокировка при сворачивании: уход в фон (ON_STOP) → возврат (ON_START).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> appLock.onEnterBackground()
                Lifecycle.Event.ON_START -> appLock.onEnterForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // глобальные ошибки — только то, что не принадлежит экрану
    LaunchedEffect(Unit) {
        errorBus.events.collect { error ->
            when (error) {
                is GlobalError.Unauthorized -> {
                    // Токен умер: если есть PIN — показываем разблокировку (далее тихий ре-логин),
                    // иначе сброс на авторизацию.
                    if (!appLock.onUnauthorized()) {
                        // Несколько экранов могли словить 401 одновременно — не дёргаем
                        // навигатор повторно, если уже стоим в auth-флоу.
                        val top = navigator.value?.lastItemOrNull
                        val inAuthFlow = top is SplashScreen || top is WelcomeScreen ||
                            top is LoginScreen || top is RegisterScreen || top is ForgotPasswordScreen
                        if (!inAuthFlow) navigator.value?.replaceAll(SplashScreen())
                    }
                }
                is GlobalError.ServerError -> {
                    globalSnackbar.showSnackbar("Ошибка сервера: ${error.code}")
                }
            }
        }
    }

    EventMapTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(globalSnackbar) }
        ) { innerPadding ->
            // innerPadding = отступы под статус-бар (батарейка/wifi) и навигацию телефона
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                Navigator(SplashScreen()) { nav ->
                    navigator.value = nav
                    // плавный слайд между экранами вместо мгновенной смены
                    SlideTransition(nav)
                }

                // Оверлей App Lock поверх навигатора — экраны под ним сохраняют состояние.
                when (lockState) {
                    AppLockState.Locked -> LockScreen(
                        controller = appLock,
                        onRecovered = {
                            // Токен ожил тихим ре-логином — гарантируем главный экран.
                            navigator.value?.replaceAll(homeProvider.home())
                        },
                        onFullLogin = {
                            appLock.onLogout()
                            navigator.value?.replaceAll(SplashScreen())
                        },
                    )
                    AppLockState.NeedsSetup -> PinSetupScreen(
                        controller = appLock,
                        onDone = { appLock.dismissSetup() },
                        onSkip = { appLock.dismissSetup() },
                    )
                    AppLockState.Unlocked -> Unit
                }
            }
        }
    }
}
