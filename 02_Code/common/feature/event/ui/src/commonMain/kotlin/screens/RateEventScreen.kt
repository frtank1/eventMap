package com.eventmap.event.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.viewmodel.RateEventViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  P4 · Оценить событие — звёзды, тег-чипсы «Что понравилось?», отзыв
//  (дизайн-прототип RATING P4)
// =====================================================================

// Теги из макета P4; коды уходят на бэк как есть (event_ratings.tags, V11)
private val ratingTags = listOf(
    "🔥 Атмосфера", "🎶 Музыка топ", "🍸 Бар", "🤝 Организация", "😕 Мало мест", "💸 Дорого",
)

data class RateEventScreen(
    val eventId: String,
    val eventTitle: String,
    val placeLabel: String = "",
    val emoji: String = "🎧",
) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<RateEventViewModel>()
        val state by vm.state.collectAsState()

        // Успех → назад (оценка попала в rating_avg и историю H1)
        LaunchedEffect(state.sent) { if (state.sent) nav.pop() }

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            // Топ-бар
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Оценить событие", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))

            // Событие
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 32.sp) }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Как прошёл $eventTitle?",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                )
                if (placeLabel.isNotBlank()) {
                    Text(placeLabel, fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Звёзды
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (1..5).forEach { i ->
                    Text(
                        if (i <= state.stars) "⭐" else "☆",
                        fontSize = 34.sp,
                        color = if (i <= state.stars) Color.Unspecified else NightTextTertiary,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickableNoRipple { vm.setStars(i) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Тег-чипсы
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("ЧТО ПОНРАВИЛОСЬ?", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(10.dp))
                // 2 ряда чипсов без FlowRow — стабильно на всех таргетах
                ratingTags.chunked(3).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        rowTags.forEach { tag ->
                            val selected = tag in state.selectedTags
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Violet600.copy(alpha = 0.28f) else NightSurface)
                                    .border(
                                        1.dp,
                                        if (selected) Violet400.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(999.dp),
                                    )
                                    .clickableNoRipple { vm.toggleTag(tag) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    tag,
                                    fontSize = 12.sp,
                                    color = if (selected) Violet400 else NightTextSecondary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Отзыв
            Column(Modifier.padding(horizontal = 20.dp)) {
                TextField(
                    value = state.review,
                    onValueChange = { vm.setReview(it) },
                    placeholder = { Text("Оставь отзыв (по желанию)…", color = NightTextSecondary, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NightSurface,
                        unfocusedContainerColor = NightSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Violet400,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "👁 Отзыв виден организатору и в рейтинге места",
                    fontSize = 11.sp, color = NightTextTertiary,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.errorText != null) {
                Text(
                    state.errorText!!,
                    color = Color(0xFFFF6B81), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (state.stars == 0 || state.sending) SolidColor(Color.White.copy(alpha = 0.12f))
                        else Brush.linearGradient(BrandGradient),
                    )
                    .clickableNoRipple { vm.submit(eventId) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.sending) "Отправляем…" else "Отправить оценку",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
