package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
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

// =====================================================================
//  Дополнительные экраны создания события
//  LocationPickerScreen   — выбор места на карте
//  EventPreviewScreen     — превью перед публикацией
//  EventPublishSuccessScreen — событие опубликовано
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)

private val popularVenues = listOf(
    Triple("🎵", "Алатау Бар", "ул. Абая 44"),
    Triple("🎭", "Театр им. Ауэзова", "пр. Абая 103"),
    Triple("🎬", "Кинотеатр Арман", "ул. Калдаякова 10"),
    Triple("🏟", "MEGA Alma-Ata", "пр. Розыбакиева 247"),
    Triple("☕", "Coffee BOOM", "ул. Панфилова 98"),
    Triple("🏛", "Центральный парк", "ул. Гоголя 20"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  LocationPickerScreen — выбор места
// ─────────────────────────────────────────────────────────────────────────────

class LocationPickerScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var searchQuery by remember { mutableStateOf("") }
        var selectedVenue by remember { mutableStateOf<Triple<String, String, String>?>(null) }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Выбор места", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            }

            // Поиск
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(46.dp)
                    .clip(RoundedCornerShape(13.dp)).background(NightSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Поиск места или адреса...", fontSize = 14.sp, color = NightTextSecondary)
                }
            }

            // Карта-заглушка
            Box(
                Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1F30)),
            ) {
                // Сетка карты
                Column(Modifier.fillMaxSize()) {
                    repeat(5) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.04f)))
                        Spacer(Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    repeat(5) {
                        Box(Modifier.fillMaxSize().width(1.dp).background(Color.White.copy(alpha = 0.04f)))
                        Spacer(Modifier.weight(1f))
                    }
                }
                // Пин
                Box(Modifier.align(Alignment.Center)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("📍", fontSize = 16.sp) }
                        Box(Modifier.size(8.dp, 4.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.30f)))
                    }
                }
                // Подсказка
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.60f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Перетащите для точного выбора", fontSize = 10.sp, color = Color.White.copy(alpha = 0.70f))
                }
                // Текущее местоположение FAB
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                        .background(NightSurface).border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("📍", fontSize = 16.sp) }
            }

            // Популярные места
            Column(Modifier.weight(1f)) {
                Row(Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)) {
                    Text("ПОПУЛЯРНЫЕ МЕСТА", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    popularVenues.forEach { venue ->
                        val sel = venue == selectedVenue
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Violet600.copy(alpha = 0.15f) else NightSurface)
                                .border(1.dp, if (sel) Violet600.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickableNoRipple { selectedVenue = venue }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(venue.first, fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(venue.second, fontSize = 13.sp, color = if (sel) Color.White else Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
                                Text(venue.third, fontSize = 11.sp, color = NightTextSecondary)
                            }
                            if (sel) Text("✓", fontSize = 16.sp, color = Violet400)
                        }
                    }
                }
            }

            // Подтвердить
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selectedVenue != null) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .border(1.dp, if (selectedVenue == null) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickableNoRipple { if (selectedVenue != null) nav.pop() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selectedVenue != null) "Выбрать: ${selectedVenue!!.second}" else "Выберите место",
                    color = if (selectedVenue != null) Color.White else NightTextSecondary,
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EventPreviewScreen — превью перед публикацией
// ─────────────────────────────────────────────────────────────────────────────

data class EventPreviewScreen(
    val title: String = "Вечер джаза «Весенние ноты»",
    val emoji: String = "🎵",
    val category: String = "Музыка",
    val date: String = "Сб, 21 июн 2026",
    val time: String = "19:00",
    val venue: String = "Алатау Бар",
    val price: String = "2 000 ₸",
    val description: String = "Приглашаем на уютный вечер живой джазовой музыки. Квинтет Артёма Воронова исполнит как классику, так и авторские композиции.",
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Превью события", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("✏️ Изменить", fontSize = 11.sp, color = NightTextSecondary) }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Обложка
                Box(
                    Modifier.fillMaxWidth().height(220.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF2D1B69), Color(0xFF0A1420)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, fontSize = 80.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                            Text(category, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(12.dp))

                    // Детали
                    listOf(
                        "📅" to "$date · $time",
                        "📍" to venue,
                        "🎟" to price,
                    ).forEach { (emoji, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(NightSurface), contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(value, fontSize = 14.sp, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    Spacer(Modifier.height(12.dp))

                    Text("Описание", fontSize = 14.sp, color = NightTextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(description, fontSize = 14.sp, color = Color.White.copy(alpha = 0.80f), lineHeight = 20.sp)

                    Spacer(Modifier.height(16.dp))

                    // Чеклист
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ГОТОВО К ПУБЛИКАЦИИ", fontSize = 10.sp, color = NightTextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(2.dp))
                            listOf("✓ Название заполнено", "✓ Дата и время указаны", "✓ Место выбрано", "✓ Описание написано", "✓ Цена установлена").forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(it.take(1), fontSize = 12.sp, color = Soft)
                                    Text(it.drop(1), fontSize = 12.sp, color = NightTextSecondary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Опубликовать
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.replace(EventPublishSuccessScreen(title, emoji)) },
                    contentAlignment = Alignment.Center,
                ) { Text("Опубликовать событие 🚀", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("Сохранить как черновик", color = NightTextSecondary, fontSize = 13.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EventPublishSuccessScreen — событие опубликовано
// ─────────────────────────────────────────────────────────────────────────────

data class EventPublishSuccessScreen(
    val eventTitle: String,
    val eventEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                    .border(3.dp, Violet400.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(eventEmoji, fontSize = 44.sp) }

            Spacer(Modifier.height(24.dp))
            Text("Событие опубликовано! 🎉", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(eventTitle, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Оно уже видно на карте и в ленте пользователей.", fontSize = 13.sp, color = NightTextTertiary, textAlign = TextAlign.Center)

            Spacer(Modifier.height(28.dp))

            // Статистика
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PublishStat("👁", "0", "просмотров")
                Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.08f)))
                PublishStat("🎟", "0", "продано")
                Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.08f)))
                PublishStat("💬", "0", "отзывов")
            }

            Spacer(Modifier.height(16.dp))

            // Что делать дальше
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("ЧТО ДАЛЬШЕ", fontSize = 10.sp, color = NightTextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(2.dp))
                NextStepRow("📤", "Поделиться событием с друзьями") {}
                NextStepRow("📊", "Следить за аналитикой в дашборде") {}
                NextStepRow("🎟", "Управление билетами") {}
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.popUntilRoot() },
                contentAlignment = Alignment.Center,
            ) { Text("На главную", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) { Text("Открыть дашборд организатора", color = NightTextSecondary, fontSize = 13.sp) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PublishStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 10.sp, color = NightTextSecondary)
    }
}

@Composable
private fun NextStepRow(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        Text("›", fontSize = 16.sp, color = NightTextTertiary)
    }
}
