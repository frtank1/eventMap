package com.eventmap

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.eventmap.core.network.error.GlobalError
import com.eventmap.core.network.error.GlobalErrorBus
import com.eventmap.auth.ui.screens.SplashScreen
import com.eventmap.theme.EventMapTheme
import org.koin.mp.KoinPlatform.getKoin

@Composable
@Preview
fun App() {
    val errorBus = remember { getKoin().get<GlobalErrorBus>() }
    val globalSnackbar = remember { SnackbarHostState() }
    val navigator = remember { mutableStateOf<Navigator?>(null) }

    // глобальные ошибки — только то, что не принадлежит экрану
    LaunchedEffect(Unit) {
        errorBus.events.collect { error ->
            when (error) {
                is GlobalError.Unauthorized -> {
                    navigator.value?.replaceAll(SplashScreen())
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
        ) { _ ->
            Navigator(SplashScreen()) { nav ->
                navigator.value = nav
                // плавный слайд между экранами вместо мгновенной смены
                SlideTransition(nav)
            }
        }
    }
}
