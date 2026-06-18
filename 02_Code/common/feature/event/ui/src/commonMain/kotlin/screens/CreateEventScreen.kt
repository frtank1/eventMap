package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.viewmodel.CreateEventViewModel
import com.eventmap.event.ui.viewmodel.CreateForm
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  D6 / D7 / D8 — Создание события (3 шага)
//  Figma «🎬 D · Событие»  nodes 51:107 / 51:124 / 51:147
// =====================================================================

private val eventCategories = listOf("🎵 Музыка", "🧘 Спорт", "🍔 Скидки", "📚 Лекция", "🎤 Стендап", "🎨 Арт", "🎮 Игры", "🌿 Природа")

class CreateEventScreen : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<CreateEventViewModel>()
        val step by vm.step.collectAsState()
        val form by vm.form.collectAsState()
        val loading by vm.loading.collectAsState()
        val created by vm.created.collectAsState()

        LaunchedEffect(created) { if (created) nav.pop() }

        Column(
            Modifier.fillMaxSize().background(NightBackground),
        ) {
            // Step progress bar (3 equal segments)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(999.dp))
                            .background(
                                if (i <= step) Brush.linearGradient(BrandGradient)
                                else Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f)),
                                ),
                            ),
                    )
                }
            }

            val titles = listOf("Что за событие?", "Где это будет?", "Детали")
            val subs = listOf("Шаг 1 из 3", "Шаг 2 из 3", "Шаг 3 из 3")

            Text(
                titles[step],
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Text(
                subs[step],
                color = NightTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp),
            )

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                when (step) {
                    0 -> StepBasics(form.title, form.description,
                        onTitle = { v -> vm.update { it.copy(title = v) } },
                        onDesc = { v -> vm.update { it.copy(description = v) } },
                    )
                    1 -> StepLocation(form.locationName,
                        onAddress = { v -> vm.update { it.copy(locationName = v) } },
                    )
                    else -> StepDetails(
                        form = form,
                        onDate = { v -> vm.update { it.copy(date = v) } },
                        onTime = { v -> vm.update { it.copy(time = v) } },
                        onCategory = { v -> vm.update { it.copy(category = v) } },
                        onPaid = { v -> vm.update { it.copy(isPaid = v) } },
                        onPrice = { v -> vm.update { it.copy(priceAmount = v) } },
                        onLimit = { v -> vm.update { it.copy(limit = v) } },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Bottom CTA row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 0) {
                    Box(
                        Modifier.width(96.dp).height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickableNoRipple { vm.back() },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹ Назад", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    Modifier.weight(1f).height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { if (step < 2) vm.next() else vm.publish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (step) {
                            0 -> "Далее → локация"
                            1 -> "Далее → детали"
                            else -> if (loading) "Публикуем…" else "✓ Опубликовать"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D6 — Шаг 1: Что за событие?
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepBasics(title: String, description: String, onTitle: (String) -> Unit, onDesc: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f)).clickableNoRipple { /* open picker */ },
        contentAlignment = Alignment.Center,
    ) { Text("🖼️  Добавить обложку", color = NightTextSecondary, fontSize = 13.sp) }
    Spacer(Modifier.height(20.dp))
    FormLabel("НАЗВАНИЕ")
    FormInput("Напр. «Йога на рассвете»", title, onTitle)
    Spacer(Modifier.height(16.dp))
    FormLabel("ОПИСАНИЕ")
    FormInput("Расскажи, что будет, для кого…", description, onDesc, height = 80.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
//  D7 — Шаг 2: Локация
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepLocation(address: String, onAddress: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C1825)),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.08f)))
        Text("📍", fontSize = 28.sp, modifier = Modifier.align(Alignment.Center).offset(y = (-14).dp))
        Box(
            Modifier.fillMaxWidth().padding(14.dp).height(38.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF11212F)).padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text("🔍  Поиск адреса или точка на карте", color = NightTextSecondary, fontSize = 12.sp) }
    }
    Spacer(Modifier.height(16.dp))
    FormLabel("АДРЕС")
    FormInput("Триатлон-парк, главный вход", address, onAddress)
    Spacer(Modifier.height(16.dp))
    FormLabel("МЕТКА")
    FormInput("Доп. ориентир (необязательно)", "", {})
}

// ─────────────────────────────────────────────────────────────────────────────
//  D8 — Шаг 3: Детали + 18+
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDetails(
    form: CreateForm,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onCategory: (String) -> Unit,
    onPaid: (Boolean) -> Unit,
    onPrice: (String) -> Unit,
    onLimit: (String) -> Unit,
) {
    FormLabel("ДАТА")
    FormInput("5 июля", form.date, onDate)
    Spacer(Modifier.height(16.dp))
    FormLabel("ВРЕМЯ")
    FormInput("08:30", form.time, onTime, keyboardType = KeyboardType.Number)
    Spacer(Modifier.height(16.dp))
    FormLabel("КАТЕГОРИЯ")
    Spacer(Modifier.height(8.dp))
    eventCategories.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { c ->
                val sel = form.category == c
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Violet600.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .clickableNoRipple { onCategory(c) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        c,
                        color = if (sel) Violet400 else NightTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(10.dp))
    FormLabel("ЦЕНА")
    FormInput("Бесплатно", form.priceAmount, onPrice, keyboardType = KeyboardType.Number)
    Spacer(Modifier.height(16.dp))
    FormLabel("ЛИМИТ МЕСТ")
    FormInput("∞", form.limit, onLimit, keyboardType = KeyboardType.Number)
    Spacer(Modifier.height(16.dp))

    // 18+ toggle row
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔞", fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Только 18+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "скроется у несовершеннолетних, бейдж на рилсе",
                color = NightTextSecondary,
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = form.isPaid,
            onCheckedChange = { onPaid(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet600,
                uncheckedThumbColor = NightTextTertiary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared form UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        color = NightTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun FormInput(
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    height: Dp = 46.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val multiline = height > 50.dp
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp),
        contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Violet400),
            modifier = Modifier.fillMaxWidth()
                .then(if (multiline) Modifier.padding(vertical = 14.dp) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = NightTextSecondary, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
