package com.eventmap.auth.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.AuthRepository
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.screens.components.BrandLogo
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  A1 — Сплэш: логотип с фиолетовым глоу + полоса загрузки
// =====================================================================

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val repo = remember { getKoin().get<AuthRepository>() }
        val homeProvider = remember { getKoin().get<HomeScreenProvider>() }

        var progress by remember { mutableStateOf(0f) }
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        )

        LaunchedEffect(Unit) {
            progress = 1f
            delay(900)
            if (repo.isLoggedIn()) nav.replaceAll(homeProvider.home())
            else nav.replaceAll(WelcomeScreen())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground),
            contentAlignment = Alignment.Center,
        ) {
            // Концентрические кольца-глоу
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Violet600.copy(alpha = 0.15f), Color.Transparent)),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Violet600.copy(alpha = 0.10f), Color.Transparent)),
                    ),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandLogo(size = 88)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "EventMap",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "карта живых событий",
                        color = NightTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Полоса загрузки
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(BrandGradient)),
                    )
                }
            }
        }
    }
}
