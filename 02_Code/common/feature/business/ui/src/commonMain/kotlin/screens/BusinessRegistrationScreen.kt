package com.eventmap.business.ui.screens

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.AnimatedPin
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.pinEffectByName
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  M1 · Промо    M2-M5 · Шаги регистрации    M6 · Статус заявки
// =====================================================================

private val businessCategories = listOf(
    "🎵" to "Музыка", "☕" to "Кафе", "🏋️" to "Спорт", "🎨" to "Арт",
    "🍕" to "Ресторан", "🎭" to "Театр", "🛍" to "Магазин", "🏨" to "Отель",
)

private val pinColors = listOf(
    Color(0xFF7C5CFF), Color(0xFFFF5C8A), Color(0xFF5DCAA5), Color(0xFF85B7EB), Color(0xFFFFB300),
)

class BusinessPromoScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState()),
        ) {
            // Hero
            Box(
                Modifier.fillMaxWidth().height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1A0D3A), Color(0xFF0A1420)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🏪", fontSize = 52.sp)
                    Text("Твой бизнес —\nживая точка на карте", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, textAlign = TextAlign.Center, lineHeight = 28.sp)
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))

                // Преимущества
                val benefits = listOf(
                    "📍" to "Фирменный пин с логотипом на карте",
                    "✨" to "Анимации и подсветка по категории",
                    "✓" to "События с бейджем верифицированного бизнеса",
                    "📊" to "Статистика посещений и охвата",
                    "🎮" to "Создание промо-квестов для клиентов",
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightSurface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    benefits.forEach { (emoji, text) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center) { Text(emoji, fontSize = 14.sp) }
                            Spacer(Modifier.width(12.dp))
                            Text(text, fontSize = 14.sp, color = Color.White, lineHeight = 18.sp)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.push(BusinessRegStep1Screen()) },
                    contentAlignment = Alignment.Center,
                ) { Text("Зарегистрировать бизнес →", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Вспомогательный прогресс-бар шагов
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepProgress(step: Int, total: Int = 4) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Шаг $step из $total", fontSize = 12.sp, color = NightTextSecondary)
            Text("${(step * 100 / total)}%", fontSize = 12.sp, color = Violet400)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { step.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
            color = Violet600, trackColor = Color.White.copy(alpha = 0.08f), strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RegTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
            .clickableNoRipple(onBack), contentAlignment = Alignment.Center) {
            Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M2: Шаг 1 — Данные компании
// ─────────────────────────────────────────────────────────────────────────────

class BusinessRegStep1Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var bin by remember { mutableStateOf("") }
        var brand by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        val binValid = bin.length >= 12

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Данные компании") { nav.pop() }
            StepProgress(1)
            Spacer(Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // БИН/ИИН
                Column {
                    OutlinedTextField(value = bin, onValueChange = { if (it.length <= 12) bin = it },
                        label = { Text("БИН / ИИН") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors, modifier = Modifier.fillMaxWidth())
                    if (binValid) {
                        Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", fontSize = 12.sp, color = Soft, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Text("ТОО «EventMap KZ» — найдено в реестре", fontSize = 12.sp, color = Soft)
                        }
                    }
                }
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Название бренда (на карте)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (binValid && brand.isNotBlank()) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (binValid && brand.isNotBlank()) nav.push(BusinessRegStep2Screen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Далее →", color = if (binValid && brand.isNotBlank()) Color.White else NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M3: Шаг 2 — Категория
// ─────────────────────────────────────────────────────────────────────────────

class BusinessRegStep2Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selected by remember { mutableStateOf<Int?>(null) }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Категория бизнеса") { nav.pop() }
            StepProgress(2)
            Spacer(Modifier.height(8.dp))

            Text("Выбери основную категорию — от неё зависит набор анимаций пина",
                fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            val rows = businessCategories.chunked(4)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIdx, (emoji, label) ->
                            val i = rowIdx * 4 + colIdx
                            val sel = i == selected
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                                    .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickableNoRipple { selected = i }
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(emoji, fontSize = 24.sp)
                                Text(label, fontSize = 11.sp, color = if (sel) Violet400 else NightTextSecondary,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (selected != null) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (selected != null) nav.push(BusinessRegStep3Screen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Далее →", color = if (selected != null) Color.White else NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M4: Шаг 3 — Пин на карте
// ─────────────────────────────────────────────────────────────────────────────

class BusinessRegStep3Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedColor by remember { mutableStateOf(0) }
        var selectedAnim by remember { mutableStateOf(0) }
        val animations = listOf("Пульс", "Волна", "Свечение", "Мигание", "⭐ Премиум")

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Отображение на карте") { nav.pop() }
            StepProgress(3)
            Spacer(Modifier.height(16.dp))

            // Превью пина
            Box(
                Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0D1A2A), Color(0xFF112030)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (animations[selectedAnim] == "⭐ Премиум") {
                        LottiePin()
                    } else {
                        AnimatedPin(
                            effect = pinEffectByName(animations[selectedAnim]),
                            color = pinColors[selectedColor],
                            emoji = "☕",
                        )
                    }
                    Text("Мой бизнес", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(pinColors[selectedColor].copy(alpha = 0.20f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text(animations[selectedAnim], fontSize = 10.sp, color = pinColors[selectedColor]) }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Цвет подсветки
            Text("ЦВЕТ ПОДСВЕТКИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pinColors.forEachIndexed { i, color ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(color)
                            .then(if (i == selectedColor) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickableNoRipple { selectedColor = i },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Анимации
            Text("АНИМАЦИЯ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(animations.indices.toList()) { i ->
                    val sel = i == selectedAnim
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (sel) pinColors[selectedColor].copy(alpha = 0.20f) else NightSurface)
                            .border(1.dp, if (sel) pinColors[selectedColor].copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickableNoRipple { selectedAnim = i }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(animations[i], fontSize = 13.sp, color = if (sel) pinColors[selectedColor] else NightTextSecondary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { nav.push(BusinessRegStep4Screen()) },
                contentAlignment = Alignment.Center,
            ) { Text("Далее →", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M5: Шаг 4 — Документы
// ─────────────────────────────────────────────────────────────────────────────

class BusinessRegStep4Screen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var certUploaded by remember { mutableStateOf(false) }
        var licenseUploaded by remember { mutableStateOf(false) }
        var agreed by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Документы") { nav.pop() }
            StepProgress(4)
            Spacer(Modifier.height(8.dp))

            Text("До подтверждения профиль работает. Бейдж ✓, пин и анимации — после модерации.",
                fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DocumentRow("Свидетельство о регистрации", "обязательно", certUploaded) { certUploaded = !certUploaded }
                DocumentRow("Лицензия / разрешение", "опционально", licenseUploaded) { licenseUploaded = !licenseUploaded }
            }

            Spacer(Modifier.height(16.dp))

            // Чекбокс полномочий
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(NightSurface)
                    .clickableNoRipple { agreed = !agreed }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (agreed) Violet600 else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (agreed) Violet600 else Color.White.copy(alpha = 0.20f), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (agreed) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Подтверждаю, что являюсь уполномоченным представителем организации", fontSize = 13.sp, color = Color.White, lineHeight = 17.sp)
            }

            Spacer(Modifier.height(24.dp))

            val canSubmit = certUploaded && agreed
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (canSubmit) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                    .clickableNoRipple { if (canSubmit) nav.push(BusinessStatusScreen()) },
                contentAlignment = Alignment.Center,
            ) { Text("📨 Отправить на проверку", color = if (canSubmit) Color.White else NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DocumentRow(label: String, hint: String, uploaded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (uploaded) Soft.copy(alpha = 0.08f) else NightSurface)
            .border(1.dp, if (uploaded) Soft.copy(alpha = 0.30f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickableNoRipple(onToggle).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (uploaded) "✅" else "📄", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(hint, fontSize = 11.sp, color = NightTextSecondary)
        }
        Text(if (uploaded) "Загружен" else "+ Загрузить", fontSize = 12.sp, color = if (uploaded) Soft else Violet400, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M6: Статус заявки
// ─────────────────────────────────────────────────────────────────────────────

class BusinessStatusScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val steps = listOf(
            Triple("Отправлена", "Сегодня, 14:32", true),
            Triple("Проверка модератором", "БИН, документы, бренд", false),
            Triple("Результат", "Пуш + письмо", false),
        )

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Статус заявки") { nav.pop() }
            Spacer(Modifier.height(24.dp))

            // Тайм-лайн
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                steps.forEachIndexed { i, (title, sub, done) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (done) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                    .border(if (!done) 1.dp else 0.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (done) "✓" else "${i + 1}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            if (i < steps.lastIndex) {
                                Box(Modifier.width(2.dp).height(40.dp).background(Color.White.copy(alpha = 0.08f)))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.padding(top = 6.dp)) {
                            Text(title, fontSize = 14.sp, color = if (done) Color.White else NightTextSecondary, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal)
                            Text(sub, fontSize = 11.sp, color = NightTextTertiary, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
                        }
                    }
                }
            }

            // Info карточка
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Info.copy(alpha = 0.08f))
                    .border(1.dp, Info.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ℹ️", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text("Проверка занимает 1–2 рабочих дня. Пока можешь создать первое событие!", fontSize = 13.sp, color = Info, lineHeight = 18.sp)
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient)).clickableNoRipple { nav.pop() },
                contentAlignment = Alignment.Center,
            ) { Text("Создать первое событие", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private val NightTextTertiary = Color(0xFF56657A)
