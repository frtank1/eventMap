package com.eventmap.payment.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.eventmap.theme.Danger
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  P1 · Оплата билета
//  P2 · Успех
//  P3 · Ошибка
//  P4 · Оценка события
// =====================================================================

data class PaymentScreen(
    val eventId: String,
    val eventTitle: String,
    val pricePerTicket: Int,       // тиын → ÷100 для отображения
    val currency: String = "₸",
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var phase by remember { mutableStateOf(PayPhase.Checkout) }
        var errorMsg by remember { mutableStateOf("") }

        when (phase) {
            PayPhase.Checkout -> CheckoutView(
                eventTitle = eventTitle,
                pricePerTicket = pricePerTicket,
                currency = currency,
                onPay = { phase = PayPhase.Success },
                onError = { errorMsg = it; phase = PayPhase.Error },
                onBack = { nav.pop() },
            )
            PayPhase.Success -> SuccessView(
                eventTitle = eventTitle,
                onReview = { phase = PayPhase.Review },
                onClose = { nav.pop() },
            )
            PayPhase.Error -> ErrorView(
                message = errorMsg,
                onRetry = { phase = PayPhase.Checkout },
                onClose = { nav.pop() },
            )
            PayPhase.Review -> ReviewView(
                eventTitle = eventTitle,
                onSend = { nav.pop() },
                onSkip = { nav.pop() },
            )
        }
    }
}

private enum class PayPhase { Checkout, Success, Error, Review }

// ─────────────────────────────────────────────────────────────────────────────
//  P1: Оформление
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckoutView(
    eventTitle: String,
    pricePerTicket: Int,
    currency: String,
    onPay: () -> Unit,
    onError: (String) -> Unit,
    onBack: () -> Unit,
) {
    var count by remember { mutableIntStateOf(1) }
    var selectedMethod by remember { mutableStateOf(0) }
    val methods = listOf("Kaspi Pay" to "🏦", "Карта" to "💳", "Apple Pay" to "")
    val total = count * pricePerTicket

    Column(
        Modifier
            .fillMaxSize()
            .background(NightBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        // Топ-бар
        Row(
            Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text("Оплата билета", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        // Карточка события
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NightSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Violet600.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Text("🎫", fontSize = 22.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(eventTitle, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${pricePerTicket / 100} $currency / билет", fontSize = 13.sp, color = Violet400, modifier = Modifier.padding(top = 3.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Счётчик билетов
        SectionLabel("КОЛИЧЕСТВО БИЛЕТОВ")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Билеты", fontSize = 14.sp, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CounterBtn("−") { if (count > 1) count-- }
                Text("$count", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                CounterBtn("+") { if (count < 10) count++ }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Дополнительные гости (анонимный +N)
        if (count > 1) {
            SectionLabel("КОМУ ПОКУПАЕШЬ")
            Column(
                Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(count - 1) { i ->
                    GuestRow(index = i + 1)
                }
                Text(
                    "ℹ️ Гости без аккаунта учтены в счётчике анонимно: «+${count - 1}»",
                    fontSize = 11.sp, color = NightTextSecondary, lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Способ оплаты
        SectionLabel("СПОСОБ ОПЛАТЫ")
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            methods.forEachIndexed { i, (label, emoji) ->
                val sel = i == selectedMethod
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Violet600.copy(alpha = 0.15f) else NightSurface)
                        .border(1.dp, if (sel) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickableNoRipple { selectedMethod = i }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (emoji.isNotBlank()) { Text(emoji, fontSize = 18.sp); Spacer(Modifier.width(10.dp)) }
                        Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    if (sel) Box(Modifier.size(18.dp).clip(CircleShape).background(Violet600), contentAlignment = Alignment.Center) {
                        Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Итого
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Итого", fontSize = 16.sp, color = NightTextSecondary, fontWeight = FontWeight.Medium)
            Text("${total / 100} $currency", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(16.dp))

        // Кнопка оплаты
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onPay),
            contentAlignment = Alignment.Center,
        ) {
            Text("Оплатить ${total / 100} $currency", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun GuestRow(index: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(NightBackground), contentAlignment = Alignment.Center) {
            Text("$index", fontSize = 12.sp, color = NightTextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text("Без аккаунта (анонимно)", fontSize = 13.sp, color = NightTextSecondary)
        Spacer(Modifier.weight(1f))
        Text("Изменить", fontSize = 11.sp, color = Violet400, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CounterBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  P2: Успех
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessView(eventTitle: String, onReview: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Иконка успеха
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Soft.copy(alpha = 0.15f))
                .border(2.dp, Soft.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✓", fontSize = 36.sp, color = Soft, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(20.dp))
        Text("Билеты у тебя!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(eventTitle, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))

        // QR-билет
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text("QR", fontSize = 18.sp, color = Color.Black, fontWeight = FontWeight.Bold) }
            Text("Покажи на входе", fontSize = 13.sp, color = NightTextSecondary)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Text("✓ Ты в списке «идут»", fontSize = 13.sp, color = Soft, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("↗ Поделиться", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple(onClose),
                contentAlignment = Alignment.Center,
            ) { Text("К событию →", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  P3: Ошибка
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Danger.copy(alpha = 0.12f)).border(2.dp, Danger.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✕", fontSize = 32.sp, color = Danger, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(20.dp))
        Text("Оплата не прошла", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message.ifBlank { "Деньги не списаны. Попробуй ещё раз или смени способ оплаты." }, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)

        Spacer(Modifier.height(32.dp))

        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onRetry),
            contentAlignment = Alignment.Center,
        ) { Text("Повторить", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f))
                .clickableNoRipple(onRetry),
            contentAlignment = Alignment.Center,
        ) { Text("Сменить способ оплаты", color = NightTextSecondary, fontSize = 14.sp) }

        Spacer(Modifier.height(12.dp))
        Text("Отмена", color = NightTextTertiary, fontSize = 14.sp, modifier = Modifier.clickableNoRipple(onClose))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  P4: Оценка события
// ─────────────────────────────────────────────────────────────────────────────

private val reviewTags = listOf("Атмосфера🔥", "Смешно😂", "Артисты топ🎤", "Дорогой бар🍺", "Мало мест🪑", "Хочу ещё✨")

@Composable
private fun ReviewView(eventTitle: String, onSend: () -> Unit, onSkip: () -> Unit) {
    var stars by remember { mutableIntStateOf(0) }
    val selectedTags = remember { mutableStateOf(setOf<String>()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NightBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Как прошёл?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Text(eventTitle, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(28.dp))

        // Звёзды
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..5).forEach { i ->
                Text(
                    if (i <= stars) "★" else "☆",
                    fontSize = 36.sp,
                    color = if (i <= stars) Color(0xFFFFD700) else NightTextSecondary,
                    modifier = Modifier.clickableNoRipple { stars = i },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Быстрые теги
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reviewTags.take(3).forEach { tag ->
                val sel = tag in selectedTags.value
                TagChip(tag, sel) { selectedTags.value = if (sel) selectedTags.value - tag else selectedTags.value + tag }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reviewTags.drop(3).forEach { tag ->
                val sel = tag in selectedTags.value
                TagChip(tag, sel) { selectedTags.value = if (sel) selectedTags.value - tag else selectedTags.value + tag }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Поле отзыва
        Box(
            Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(14.dp)).background(NightSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(14.dp),
        ) { Text("Расскажи подробнее…", color = NightTextSecondary, fontSize = 14.sp) }

        Spacer(Modifier.height(8.dp))
        Text("Видно организатору и влияет на рейтинг места", fontSize = 11.sp, color = NightTextTertiary)

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onSend),
            contentAlignment = Alignment.Center,
        ) { Text("Отправить", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(10.dp))
        Text("Позже", color = NightTextSecondary, fontSize = 14.sp, modifier = Modifier.clickableNoRipple(onSkip))
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TagChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Violet600.copy(alpha = 0.25f) else NightSurface)
            .border(1.dp, if (selected) Violet600.copy(alpha = 0.60f) else Color.Transparent, RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Violet400 else NightTextSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp))
}

private val NightTextTertiary = Color(0xFF56657A)
