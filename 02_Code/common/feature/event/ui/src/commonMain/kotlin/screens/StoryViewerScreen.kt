package com.eventmap.event.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.delay

// =====================================================================
//  StoryViewerScreen — полноэкранный просмотр историй события
//  Автопрогресс 5 сек на историю, тап для переключения
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Live = Color(0xFFFF4757)
private val Coral500 = Color(0xFFFF5C8A)

private data class StorySlide(
    val id: String,
    val emoji: String,
    val bgGradient: List<Color>,
    val title: String,
    val caption: String,
    val author: String,
    val time: String,
    val views: Int,
    val reactions: Map<String, Int> = emptyMap(),
)

private val sampleStories = listOf(
    StorySlide(
        "s1", "🎵",
        listOf(Color(0xFF1A0A2E), Color(0xFF2D1B69)),
        "Jazz Квартал открылся! 🎉",
        "Первые гости уже внутри. Атмосфера невероятная — живые инструменты, свечи, джаз...",
        "Jazz Квартал", "2 мин назад", 1_204,
        mapOf("🔥" to 89, "❤️" to 54, "😍" to 41),
    ),
    StorySlide(
        "s2", "🎷",
        listOf(Color(0xFF0A1F2E), Color(0xFF0D3B5E)),
        "На сцене — квинтет Артёма",
        "Живой саксофон в исполнении Артёма Воронова. Это нужно слышать!",
        "Jazz Квартал", "15 мин назад", 870,
        mapOf("🎵" to 120, "🔥" to 67, "🥹" to 33),
    ),
    StorySlide(
        "s3", "🥂",
        listOf(Color(0xFF1A0820), Color(0xFF3D1148)),
        "Cocktail bar открыт 🍸",
        "Специальное меню этого вечера: Jazz Sour, Blue Note, Midnight Almaty",
        "Jazz Квартал", "32 мин назад", 540,
        mapOf("😋" to 78, "🥂" to 45),
    ),
    StorySlide(
        "s4", "📸",
        listOf(Color(0xFF0F1E2E), Color(0xFF1C3D5C)),
        "Фото с гостями вечера",
        "Сделали несколько кадров с нашими любимыми гостями. Вы великолепны! ✨",
        "Jazz Квартал", "1 ч назад", 2_310,
        mapOf("😍" to 198, "❤️" to 143, "🔥" to 97),
    ),
)

data class StoryViewerScreen(
    val eventId: String,
    val eventTitle: String,
    val startIndex: Int = 0,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var currentIndex by remember { mutableStateOf(startIndex) }
        var progress by remember { mutableStateOf(0f) }
        var isPaused by remember { mutableStateOf(false) }
        var reactionSent by remember { mutableStateOf("") }

        val story = sampleStories.getOrElse(currentIndex) { sampleStories.last() }

        // Автопрогресс
        LaunchedEffect(currentIndex, isPaused) {
            progress = 0f
            if (!isPaused) {
                val steps = 100
                repeat(steps) {
                    delay(50L)
                    if (!isPaused) progress = (it + 1) / steps.toFloat()
                }
                if (currentIndex < sampleStories.lastIndex) currentIndex++ else nav.pop()
            }
        }

        Box(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(story.bgGradient, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY))),
        ) {
            // Большой эмодзи-фон
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(story.emoji, fontSize = 140.sp, modifier = Modifier.padding(bottom = 80.dp))
            }

            // Прогресс-бары вверху
            Column(Modifier.fillMaxWidth().padding(top = 52.dp, start = 12.dp, end = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sampleStories.forEachIndexed { i, _ ->
                        val segmentProgress = when {
                            i < currentIndex -> 1f
                            i == currentIndex -> progress
                            else -> 0f
                        }
                        Box(
                            Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.30f)),
                        ) {
                            Box(Modifier.fillMaxWidth(segmentProgress).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Color.White))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Автор
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text(story.emoji, fontSize = 14.sp) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(story.author, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(story.time, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                        Text("✕", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            // Тап — предыд / следующ
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize().clickableNoRipple {
                    if (currentIndex > 0) { currentIndex--; progress = 0f }
                })
                Box(Modifier.weight(1f).fillMaxSize().clickableNoRipple {
                    if (currentIndex < sampleStories.lastIndex) { currentIndex++; progress = 0f } else nav.pop()
                })
            }

            // Контент внизу
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                // Заголовок + текст
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.40f)).padding(14.dp),
                ) {
                    Column {
                        Text(story.title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(story.caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.80f), lineHeight = 18.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Реакции + просмотры
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Просмотры
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👁", fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp))
                        Text("${story.views}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
                    }
                    Spacer(Modifier.width(12.dp))
                    // Реакции
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        story.reactions.entries.take(3).forEach { (emoji, count) ->
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(emoji, fontSize = 12.sp)
                                    Spacer(Modifier.width(3.dp))
                                    Text("$count", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Отправить реакцию
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableNoRipple { reactionSent = "🔥" },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (reactionSent.isNotEmpty()) reactionSent else "🔥", fontSize = 18.sp) }
                }

                Spacer(Modifier.height(8.dp))

                // Поле ответа
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ответить на историю...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.50f), modifier = Modifier.weight(1f))
                    Text("➤", fontSize = 16.sp, color = Color.White.copy(alpha = 0.60f))
                }
            }
        }
    }
}
