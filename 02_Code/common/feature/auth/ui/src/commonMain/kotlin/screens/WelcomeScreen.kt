package com.eventmap.auth.ui.screens

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.components.DotsIndicator
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import kotlinx.coroutines.launch

private data class WelcomePage(val emoji: String, val title: String, val subtitle: String)

private val welcomePages = listOf(
    WelcomePage("🗺️", "Карта живых событий", "Всё, что происходит вокруг тебя — прямо сейчас на карте"),
    WelcomePage("🔔", "Подписывайся", "Следи за жанрами, организаторами и людьми, которые тебе интересны"),
    WelcomePage("📸", "Делись моментами", "Снимай и выкладывай live-видео с событий"),
)

class WelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val pagerState = rememberPagerState(pageCount = { welcomePages.size })
        val scope = rememberCoroutineScope()
        val isLast by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf { pagerState.currentPage == welcomePages.lastIndex }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LinkText("Пропустить", onClick = { nav.push(RegisterScreen()) })
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val p = welcomePages[page]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(140.dp).clip(CircleShape)
                            .background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text(p.emoji, fontSize = 64.sp) }
                    Spacer(Modifier.height(32.dp))
                    Text(p.title, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(p.subtitle, style = MaterialTheme.typography.bodyLarge, color = EventMapTheme.colors.textSecondary, textAlign = TextAlign.Center)
                }
            }

            DotsIndicator(welcomePages.size, pagerState.currentPage, Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally))

            PrimaryGradientButton(
                text = if (isLast) "Начать" else "Далее",
                onClick = {
                    if (isLast) nav.push(RegisterScreen())
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Уже есть аккаунт? ", color = EventMapTheme.colors.textSecondary)
                LinkText("Войти", onClick = { nav.push(LoginScreen()) })
            }
        }
    }
}
