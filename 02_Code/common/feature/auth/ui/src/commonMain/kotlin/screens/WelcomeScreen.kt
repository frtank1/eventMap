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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.eventmap.auth.ui.screens.components.DotsIndicator
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.launch

// =====================================================================
//  A2–A4 — Welcome pager (3 слайда)
//  Каждый слайд: эмодзи в концентрических кольцах, заголовок, подзаголовок
// =====================================================================

private data class WelcomePage(val emoji: String, val title: String, val subtitle: String)

private val welcomePages = listOf(
    WelcomePage(
        emoji = "🗺️",
        title = "Карта живых событий",
        subtitle = "Всё, что происходит вокруг тебя — прямо сейчас на карте",
    ),
    WelcomePage(
        emoji = "🔔",
        title = "Подписывайся",
        subtitle = "Следи за жанрами, организаторами и людьми, которые тебе интересны",
    ),
    WelcomePage(
        emoji = "📸",
        title = "Делись моментами",
        subtitle = "Снимай и выкладывай live-рилсы с событий — 30 секунд живого момента",
    ),
)

class WelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val pagerState = rememberPagerState(pageCount = { welcomePages.size })
        val scope = rememberCoroutineScope()
        val isLast by remember { derivedStateOf { pagerState.currentPage == welcomePages.lastIndex } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .padding(horizontal = 24.dp),
        ) {
            // «Пропустить» вверху справа
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                LinkText("Пропустить", onClick = { nav.push(RegisterScreen()) })
            }

            // Слайды
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
                    EmojiRings(p.emoji)
                    Spacer(Modifier.height(36.dp))
                    Text(
                        p.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.subtitle,
                        fontSize = 14.sp,
                        color = NightTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // Dots
            DotsIndicator(
                count = welcomePages.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 28.dp),
            )

            // Кнопка «Далее» / «Начать 🚀»
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple {
                        if (isLast) nav.push(RegisterScreen())
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isLast) "Начать 🚀" else "Дальше →",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // «Уже есть аккаунт? Войти»
            Row(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Уже есть аккаунт? ", color = NightTextSecondary, fontSize = 14.sp)
                LinkText("Войти", onClick = { nav.push(LoginScreen()) })
            }
        }
    }
}

/** Эмодзи в трёх концентрических кольцах (глоу-эффект). */
@Composable
private fun EmojiRings(emoji: String) {
    Box(contentAlignment = Alignment.Center) {
        // Внешнее кольцо-глоу
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Violet600.copy(alpha = 0.08f), Color.Transparent)),
                ),
        )
        // Среднее кольцо
        Box(
            Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f)),
        )
        // Внутренний круг с эмодзи
        Box(
            Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 46.sp)
        }
    }
}
