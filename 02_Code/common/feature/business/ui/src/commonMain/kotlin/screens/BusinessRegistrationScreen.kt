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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.business.ui.viewmodel.BusinessMeState
import com.eventmap.business.ui.viewmodel.BusinessMeViewModel
import com.eventmap.business.ui.viewmodel.BusinessRegViewModel
import com.eventmap.business.ui.viewmodel.RegDraft
import com.eventmap.business.ui.viewmodel.SubmitState
import com.eventmap.theme.AnimatedPin
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.pinEffectByName
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch

// =====================================================================
//  M0 · Гейт    M1 · Промо    M2–M5 · Шаги заявки (живой POST /business/apply)
//  M6 · Статус (живой GET /business/me)
// =====================================================================

private val businessCategories = listOf(
    "🎵" to "Музыка", "☕" to "Кафе", "🏋️" to "Спорт", "🎨" to "Арт",
    "🍕" to "Ресторан", "🎭" to "Театр", "🛍" to "Магазин", "🏨" to "Отель",
)

private val pinColors = listOf(
    Color(0xFF7C5CFF), Color(0xFFFF5C8A), Color(0xFF5DCAA5), Color(0xFF85B7EB), Color(0xFFFFB300),
)
private val pinColorHexes = listOf("#7C5CFF", "#FF5C8A", "#5DCAA5", "#85B7EB", "#FFB300")

private const val MAX_DOC_BYTES = 8_000_000

private fun docContentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "image/jpeg"
}

// ─────────────────────────────────────────────────────────────────────────────
//  M0: Гейт — профиль ведёт сюда; экран сам решает: промо / статус / дашборд
// ─────────────────────────────────────────────────────────────────────────────

class BusinessGateScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<BusinessMeViewModel>()
        val state by vm.state.collectAsState()

        LaunchedEffect(state) {
            when (val s = state) {
                is BusinessMeState.NoBusiness -> nav.replace(BusinessPromoScreen())
                is BusinessMeState.Has ->
                    if (s.biz.status == "approved") nav.replace(BusinessOrganizerScreen(s.biz.brandName))
                    else nav.replace(BusinessStatusScreen())
                else -> Unit
            }
        }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            RegTopBar("Для бизнеса") { nav.pop() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val s = state) {
                    is BusinessMeState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", fontSize = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(s.message, fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp))
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(BrandGradient))
                                .clickableNoRipple { vm.load() }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Повторить", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                    else -> CircularProgressIndicator(color = Violet600)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M1: Промо
// ─────────────────────────────────────────────────────────────────────────────

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
                        // replace: после отправки заявки «назад» ведёт в профиль, а не на промо
                        .clickableNoRipple { nav.replace(BusinessRegistrationScreen()) },
                    contentAlignment = Alignment.Center,
                ) { Text("Зарегистрировать бизнес →", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Вспомогательные: прогресс шагов и топбар
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
internal fun RegTopBar(title: String, onBack: () -> Unit) {
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

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
            .clickableNoRipple { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Color.White else NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
}

private val fieldTitles = listOf("Данные компании", "Категория бизнеса", "Отображение на карте", "Документы")

// ─────────────────────────────────────────────────────────────────────────────
//  M2–M5: Заявка — ОДИН экран с шагами, черновик живёт в BusinessRegViewModel
// ─────────────────────────────────────────────────────────────────────────────

class BusinessRegistrationScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<BusinessRegViewModel>()
        val draft by vm.draft.collectAsState()
        val submit by vm.submit.collectAsState()
        val docUploading by vm.docUploading.collectAsState()
        val docError by vm.docError.collectAsState()
        val scope = rememberCoroutineScope()
        var step by remember { mutableStateOf(1) }

        LaunchedEffect(submit) {
            if (submit is SubmitState.Done) nav.replace(BusinessStatusScreen())
        }

        fun pickDoc(kind: String) = scope.launch {
            val file = FileKit.pickFile(type = PickerType.Image, mode = PickerMode.Single) ?: return@launch
            val bytes = file.readBytes()
            if (bytes.size > MAX_DOC_BYTES) {
                vm.setDocError("Файл больше 8 МБ — сними фото документа поменьше")
                return@launch
            }
            vm.uploadDocument(kind, bytes, docContentType(file.name))
        }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar(fieldTitles[step - 1]) { if (step > 1) step-- else nav.pop() }
            StepProgress(step)
            Spacer(Modifier.height(8.dp))

            when (step) {
                1 -> StepCompany(draft, vm::update) { step = 2 }
                2 -> StepCategory(draft, vm::update) { step = 3 }
                3 -> StepPin(draft, vm::update) { step = 4 }
                else -> StepDocs(
                    draft = draft,
                    update = vm::update,
                    uploading = docUploading,
                    errorText = docError ?: (submit as? SubmitState.Error)?.message,
                    sending = submit is SubmitState.Sending,
                    onPickDoc = { kind -> vm.setDocError(null); pickDoc(kind) },
                    onSubmit = { vm.submit() },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Шаг 1: данные компании ──

@Composable
private fun StepCompany(draft: RegDraft, update: ((RegDraft) -> RegDraft) -> Unit, onNext: () -> Unit) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
        unfocusedTextColor = Color.White, focusedTextColor = Color.White,
        unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
        unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
    )

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            OutlinedTextField(
                value = draft.bin,
                onValueChange = { v -> if (v.length <= 12 && v.all { it.isDigit() }) update { it.copy(bin = v) } },
                label = { Text("БИН / ИИН") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            if (draft.binValid) {
                Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", fontSize = 12.sp, color = Soft, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("Формат верный — реестр проверит модератор", fontSize = 12.sp, color = Soft)
                }
            }
        }
        OutlinedTextField(value = draft.legalName, onValueChange = { v -> update { it.copy(legalName = v) } },
            label = { Text("Юридическое название (ТОО/ИП)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.brand, onValueChange = { v -> update { it.copy(brand = v) } },
            label = { Text("Название бренда (на карте)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.phone, onValueChange = { v -> update { it.copy(phone = v) } },
            label = { Text("Телефон") }, singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = fieldColors, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.social, onValueChange = { v -> update { it.copy(social = v) } },
            label = { Text("Instagram / сайт (необязательно)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton("Далее →", enabled = draft.binValid && draft.brand.isNotBlank() && draft.legalName.isNotBlank(), onClick = onNext)
}

// ── Шаг 2: категория ──

@Composable
private fun StepCategory(draft: RegDraft, update: ((RegDraft) -> RegDraft) -> Unit, onNext: () -> Unit) {
    Text("Выбери основную категорию — от неё зависит набор анимаций пина",
        fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

    val rows = businessCategories.chunked(4)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (emoji, label) ->
                    val sel = label == draft.category
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                            .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { update { it.copy(category = label) } }
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
    PrimaryButton("Далее →", enabled = draft.category != null, onClick = onNext)
}

// ── Шаг 3: пин на карте (визуальный выбор; цвет уйдёт в PATCH /business/pin) ──

@Composable
private fun StepPin(draft: RegDraft, update: ((RegDraft) -> RegDraft) -> Unit, onNext: () -> Unit) {
    val animations = listOf("Пульс", "Волна", "Свечение", "Мигание", "⭐ Премиум")
    val selectedColor = pinColorHexes.indexOf(draft.pinColorHex).coerceAtLeast(0)
    val emoji = businessCategories.firstOrNull { it.second == draft.category }?.first ?: "🏪"

    // Превью пина
    Box(
        Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0D1A2A), Color(0xFF112030)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (animations[draft.pinAnimIndex] == "⭐ Премиум") {
                LottiePin()
            } else {
                AnimatedPin(
                    effect = pinEffectByName(animations[draft.pinAnimIndex]),
                    color = pinColors[selectedColor],
                    emoji = emoji,
                )
            }
            Text(draft.brand.ifBlank { "Мой бизнес" }, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(pinColors[selectedColor].copy(alpha = 0.20f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Text(animations[draft.pinAnimIndex], fontSize = 10.sp, color = pinColors[selectedColor]) }
        }
    }

    Spacer(Modifier.height(20.dp))

    Text("ЦВЕТ ПОДСВЕТКИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pinColors.forEachIndexed { i, color ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color)
                    .then(if (i == selectedColor) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickableNoRipple { update { it.copy(pinColorHex = pinColorHexes[i]) } },
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Text("АНИМАЦИЯ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(animations.indices.toList()) { i ->
            val sel = i == draft.pinAnimIndex
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (sel) pinColors[selectedColor].copy(alpha = 0.20f) else NightSurface)
                    .border(1.dp, if (sel) pinColors[selectedColor].copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickableNoRipple { update { it.copy(pinAnimIndex = i) } }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(animations[i], fontSize = 13.sp, color = if (sel) pinColors[selectedColor] else NightTextSecondary,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton("Далее →", enabled = true, onClick = onNext)
}

// ── Шаг 4: документы (реальная загрузка) + отправка ──

@Composable
private fun StepDocs(
    draft: RegDraft,
    update: ((RegDraft) -> RegDraft) -> Unit,
    uploading: Boolean,
    errorText: String?,
    sending: Boolean,
    onPickDoc: (kind: String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text("До подтверждения профиль работает. Бейдж ✓, пин и анимации — после модерации.",
        fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

    val certUploaded = draft.docs.any { it.kind == "certificate" }
    val licenseUploaded = draft.docs.any { it.kind == "license" }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DocumentRow("Свидетельство о регистрации", "обязательно · фото или скан", certUploaded, uploading) { onPickDoc("certificate") }
        DocumentRow("Лицензия / разрешение", "опционально", licenseUploaded, uploading) { onPickDoc("license") }
    }

    Spacer(Modifier.height(16.dp))

    // Чекбокс полномочий
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)).background(NightSurface)
            .clickableNoRipple { update { it.copy(agreed = !it.agreed) } }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                .background(if (draft.agreed) Violet600 else Color.White.copy(alpha = 0.08f))
                .border(1.dp, if (draft.agreed) Violet600 else Color.White.copy(alpha = 0.20f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { if (draft.agreed) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text("Подтверждаю, что являюсь уполномоченным представителем организации", fontSize = 13.sp, color = Color.White, lineHeight = 17.sp)
    }

    if (errorText != null) {
        Text(errorText, fontSize = 12.sp, color = Color(0xFFFF7A8A), lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    } else {
        Spacer(Modifier.height(24.dp))
    }

    val canSubmit = certUploaded && draft.agreed && !uploading && !sending
    PrimaryButton(if (sending) "Отправляем…" else "📨 Отправить на проверку", enabled = canSubmit, onClick = onSubmit)
}

@Composable
private fun DocumentRow(label: String, hint: String, uploaded: Boolean, uploading: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (uploaded) Soft.copy(alpha = 0.08f) else NightSurface)
            .border(1.dp, if (uploaded) Soft.copy(alpha = 0.30f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickableNoRipple { if (!uploading) onPick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (uploaded) "✅" else "📄", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(hint, fontSize = 11.sp, color = NightTextSecondary)
        }
        Text(
            when {
                uploading -> "Загрузка…"
                uploaded -> "Заменить"
                else -> "+ Загрузить"
            },
            fontSize = 12.sp, color = if (uploaded) Soft else Violet400, fontWeight = FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  M6: Статус заявки — живой GET /business/me
// ─────────────────────────────────────────────────────────────────────────────

class BusinessStatusScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<BusinessMeViewModel>()
        val state by vm.state.collectAsState()

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Статус заявки") { nav.pop() }
            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                is BusinessMeState.Loading ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Violet600)
                    }

                is BusinessMeState.NoBusiness -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📭", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Заявки пока нет", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Подать заявку", enabled = true) { nav.replace(BusinessRegistrationScreen()) }
                }

                is BusinessMeState.Error -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📡", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(s.message, fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Повторить", enabled = true) { vm.load() }
                }

                is BusinessMeState.Has -> StatusContent(
                    status = s.biz.status,
                    brand = s.biz.brandName,
                    rejectReason = s.biz.rejectReason,
                    onRefresh = { vm.load() },
                    onCreateQuest = { nav.push(QuestBuilderScreen()) },
                    onOpenDashboard = { nav.push(BusinessOrganizerScreen(s.biz.brandName)) },
                    onReapply = { nav.replace(BusinessRegistrationScreen()) },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatusContent(
    status: String,
    brand: String,
    rejectReason: String?,
    onRefresh: () -> Unit,
    onCreateQuest: () -> Unit,
    onOpenDashboard: () -> Unit,
    onReapply: () -> Unit,
) {
    val decided = status != "pending"
    val steps = listOf(
        Triple("Отправлена", brand, true),
        Triple("Проверка модератором", "БИН, документы, бренд", decided),
        Triple("Результат", if (status == "approved") "Одобрено ✓" else if (status == "rejected") "Отклонено" else "Пуш + письмо", decided),
    )

    // Тайм-лайн
    Column(Modifier.padding(horizontal = 20.dp)) {
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

    when (status) {
        "approved" -> {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Soft.copy(alpha = 0.08f))
                    .border(1.dp, Soft.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎉", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text("Бизнес подтверждён! Пин, бейдж ✓ и промо-квесты доступны.", fontSize = 13.sp, color = Soft, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("🎮 Создать промо-квест", enabled = true, onClick = onCreateQuest)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("📊 Дашборд организатора", enabled = true, onClick = onOpenDashboard)
        }

        "rejected" -> {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFFE24B4A).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFE24B4A).copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("😔", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    rejectReason?.takeIf { it.isNotBlank() }?.let { "Отклонено: $it" } ?: "Заявка отклонена. Поправь данные и отправь снова.",
                    fontSize = 13.sp, color = Color(0xFFFF7A8A), lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Исправить и отправить снова", enabled = true, onClick = onReapply)
        }

        else -> {
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
            PrimaryButton("Обновить статус", enabled = true, onClick = onRefresh)
        }
    }
}

private val NightTextTertiary = Color(0xFF56657A)
