package com.eventmap.business.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.business.ui.viewmodel.DraftPoint
import com.eventmap.business.ui.viewmodel.QuestBuilderViewModel
import com.eventmap.business.ui.viewmodel.QuestSubmitState
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Конструктор промо-квеста (только approved-бизнес; POST /quests).
//  Точки: 1–10, условия geo / photo / purchase, координаты — «Я на месте».
//  После отправки квест уходит на модерацию (/admin), потом попадает в каталог.
// =====================================================================

private val questEmojis = listOf("🎯", "🍔", "☕", "🎨", "🏃", "🎁", "🎮", "📸")
private val conditions = listOf("geo" to "📍 Гео", "photo" to "📸 Фото", "purchase" to "🧾 Покупка")
private val durations = listOf(7, 14, 30)

private fun coordLabel(lat: Double, lng: Double): String {
    fun r(v: Double) = (v * 100000).toInt() / 100000.0
    return "${r(lat)}, ${r(lng)}"
}

class QuestBuilderScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<QuestBuilderViewModel>()
        val draft by vm.draft.collectAsState()
        val submit by vm.submit.collectAsState()
        val locating by vm.locating.collectAsState()

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface, focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White, focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary, focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f), focusedBorderColor = Violet600, cursorColor = Violet600,
        )

        // Успех — отдельный полноэкранный стейт
        (submit as? QuestSubmitState.Done)?.let { done ->
            Column(
                Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🎉", fontSize = 56.sp)
                Spacer(Modifier.height(14.dp))
                Text("«${done.title}» отправлен\nна модерацию", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, textAlign = TextAlign.Center, lineHeight = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text("Появится в каталоге квестов после одобрения — обычно в течение дня.",
                    fontSize = 13.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("Готово", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            }
            return
        }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            RegTopBar("Новый промо-квест") { nav.pop() }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Название + эмодзи
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { v -> vm.update { it.copy(title = v) } },
                    label = { Text("Название («Тур по бургерным»)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    questEmojis.forEach { e ->
                        val sel = e == draft.emoji
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (sel) Violet600.copy(alpha = 0.25f) else NightSurface)
                                .border(1.dp, if (sel) Violet600 else Color.Transparent, CircleShape)
                                .clickableNoRipple { vm.update { it.copy(emoji = e) } },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }

                // Награда
                OutlinedTextField(
                    value = draft.prize,
                    onValueChange = { v -> vm.update { it.copy(prize = v) } },
                    label = { Text("Награда («Бесплатный кофе»)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.prizeCount,
                        onValueChange = { v -> if (v.all { c -> c.isDigit() }) vm.update { it.copy(prizeCount = v) } },
                        label = { Text("Призов (шт)") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.participantLimit,
                        onValueChange = { v -> if (v.all { c -> c.isDigit() }) vm.update { it.copy(participantLimit = v) } },
                        label = { Text("Лимит участников") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors, modifier = Modifier.weight(1f),
                    )
                }

                // Срок
                Column {
                    Text("СРОК", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        durations.forEach { d ->
                            val sel = d == draft.days
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) Violet600.copy(alpha = 0.20f) else NightSurface)
                                    .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                                    .clickableNoRipple { vm.update { it.copy(days = d) } }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) { Text("$d дней", fontSize = 13.sp, color = if (sel) Violet400 else NightTextSecondary) }
                        }
                    }
                }

                // Порядок прохождения
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NightSurface)
                        .clickableNoRipple { vm.update { it.copy(orderedChain = !it.orderedChain) } }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (draft.orderedChain) Violet600 else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (draft.orderedChain) Violet600 else Color.White.copy(alpha = 0.20f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (draft.orderedChain) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Проходить по порядку", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Точки открываются одна за другой, как цепочка", fontSize = 11.sp, color = NightTextSecondary)
                    }
                }

                // Точки
                Text("ТОЧКИ · ${draft.points.size} из 10", color = NightTextSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

                draft.points.forEachIndexed { i, point ->
                    PointCard(
                        index = i,
                        point = point,
                        fieldColors = fieldColors,
                        removable = draft.points.size > 1,
                        locating = locating == i,
                        onName = { v -> vm.updatePoint(i) { it.copy(placeName = v) } },
                        onCondition = { c -> vm.updatePoint(i) { it.copy(condition = c) } },
                        onLocate = { vm.locatePoint(i) },
                        onRemove = { vm.removePoint(i) },
                    )
                }

                if (draft.points.size < 10) {
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                            .background(NightSurface)
                            .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickableNoRipple { vm.addPoint() },
                        contentAlignment = Alignment.Center,
                    ) { Text("＋ Добавить точку", color = Violet400, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }

                (submit as? QuestSubmitState.Error)?.let { err ->
                    Text(err.message, fontSize = 12.sp, color = Color(0xFFFF7A8A), lineHeight = 16.sp)
                }

                val sending = submit is QuestSubmitState.Sending
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (!sending) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                        .clickableNoRipple { if (!sending) { vm.resetError(); vm.submit() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (sending) "Отправляем…" else "🚀 Отправить на модерацию",
                        color = if (sending) NightTextSecondary else Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    "Квест проверит модератор — после одобрения он появится в каталоге и на карте.",
                    fontSize = 11.sp, color = NightTextSecondary, lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PointCard(
    index: Int,
    point: DraftPoint,
    fieldColors: androidx.compose.material3.TextFieldColors,
    removable: Boolean,
    locating: Boolean,
    onName: (String) -> Unit,
    onCondition: (String) -> Unit,
    onLocate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Точка ${index + 1}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (removable) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple(onRemove),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 12.sp, color = NightTextSecondary) }
            }
        }

        OutlinedTextField(
            value = point.placeName,
            onValueChange = onName,
            label = { Text("Место («Кофейня на Кабанбай батыра»)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            conditions.forEach { (value, label) ->
                val sel = value == point.condition
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (sel) Violet600.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (sel) Violet600.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(999.dp))
                        .clickableNoRipple { onCondition(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(label, fontSize = 12.sp, color = if (sel) Violet400 else NightTextSecondary) }
            }
        }
        if (point.condition == "purchase") {
            Text("Покупку подтверждает QR-код с кассы — распечатаешь его после одобрения квеста.",
                fontSize = 11.sp, color = NightTextSecondary, lineHeight = 15.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (point.lat != null && point.lng != null) {
                    Text("✓ ${coordLabel(point.lat, point.lng)}", fontSize = 12.sp, color = Soft, fontWeight = FontWeight.Medium)
                } else {
                    Text("Координат пока нет", fontSize = 12.sp, color = NightTextSecondary)
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { if (!locating) onLocate() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (locating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("📍 Я на месте", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
