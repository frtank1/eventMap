package com.eventmap.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.AuthRepository
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.screens.components.BrandLogo
import com.eventmap.theme.EventMapTheme
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform.getKoin

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val repo = remember { getKoin().get<AuthRepository>() }
        val homeProvider = remember { getKoin().get<HomeScreenProvider>() }

        LaunchedEffect(Unit) {
            delay(900)
            if (repo.isLoggedIn()) nav.replaceAll(homeProvider.home())
            else nav.replaceAll(WelcomeScreen())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandLogo(size = 96)
                Text(
                    "EventMap",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                )
            }
        }
    }
}
