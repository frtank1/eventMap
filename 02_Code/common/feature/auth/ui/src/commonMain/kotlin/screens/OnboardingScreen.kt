package com.eventmap.auth.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================
//  A1 · Сплеш    A2 · Онбординг (3 слайда)
// =====================================================================

/** A1 v2 — splash с горизонтальным onboarding (заменяет SplashScreen + WelcomeScreen) */
class SplashIntroScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            visible = true
            delay(2000)
            nav.replace(OnboardingV2Screen())
        }

        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0A1A), NightBackground))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text("📍", fontSize = 44.sp) }
                Text("EventMap", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.5).sp)
                Text("Живая карта города", fontSize = 14.sp, color = NightTextSecondary, letterSpacing = 0.3.sp)
            }
        }
    }
}

private data class OnboardSlide(val emoji: String, val title: String, val sub: String, val bg: List<Color>)

private val onboardSlides = listOf(
    OnboardSlide("🗺️", "Карта, которая живёт", "Все события города — концерты, вечеринки, маркеты — в реальном времени на одной карте.",
        listOf(Color(0xFF0D1A2A), Color(0xFF0A1420))),
    OnboardSlide("🎬", "Рилсы вместо афиш", "Листай видео-превью событий как в ленте. Находи только то, что тебя зажигает.",
        listOf(Color(0xFF1A0D2A), Color(0xFF0A1420))),
    OnboardSlide("🎮", "Участвуй и получай", "XP за посещения, бейджи, квесты от брендов и место в топе активных горожан.",
        listOf(Color(0xFF0D2A1A), Color(0xFF0A1420))),
)

class OnboardingV2Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val pager = rememberPagerState { onboardSlides.size }
        val scope = rememberCoroutineScope()

        Box(Modifier.fillMaxSize().background(NightBackground)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val slide = onboardSlides[page]
                Box(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(slide.bg)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.padding(horizontal = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(slide.emoji, fontSize = 80.sp, textAlign = TextAlign.Center)
                        Text(slide.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                            textAlign = TextAlign.Center, lineHeight = 32.sp)
                        Text(slide.sub, fontSize = 15.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp)
                    }
                }
            }

            // Индикаторы + кнопки
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Точки
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(onboardSlides.size) { i ->
                        val active = i == pager.currentPage
                        Box(
                            Modifier.height(6.dp)
                                .width(if (active) 24.dp else 6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Violet600 else Color.White.copy(alpha = 0.25f)),
                        )
                    }
                }

                // Кнопки
                if (pager.currentPage < onboardSlides.lastIndex) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickableNoRipple { nav.push(AuthScreen()) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Пропустить", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp) }
                        Box(
                            Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(BrandGradient))
                                .clickableNoRipple { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            contentAlignment = Alignment.Center,
                        ) { Text("Далее →", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple { nav.push(AuthScreen()) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Начать 🚀", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}
