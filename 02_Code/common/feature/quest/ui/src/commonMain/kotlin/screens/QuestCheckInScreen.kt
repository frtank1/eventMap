package com.eventmap.quest.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.koin.koinScreenModel
import com.eventmap.quest.ui.viewmodel.QuestCheckInViewModel
import com.eventmap.views.qr.QrScannerView
import com.eventmap.views.qr.qrScannerAvailable
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
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
import com.eventmap.quest.ui.models.CheckInType
import com.eventmap.quest.ui.models.QuestItem
import com.eventmap.quest.ui.models.QuestPoint
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Q3 · Чек-ин точки     Q4 · Финиш квеста
// =====================================================================

data class QuestCheckInScreen(val point: QuestPoint, val quest: QuestItem) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        // Реальный чек-ин через POST /quests/{id}/points/{pid}/checkin (sample-квесты — локально)
        val vm = koinScreenModel<QuestCheckInViewModel>()
        val state by vm.state.collectAsState()
        var photoTooBig by remember { mutableStateOf(false) }

        when (val s = state) {
            is QuestCheckInViewModel.State.Success ->
                if (s.finished) {
                    QuestFinishView(
                        quest = quest,
                        xpGained = s.xpGained,
                        prizeQr = s.prizeQr,
                        onClose = { nav.popUntilRoot() }, // каталог квестов — таб MainScreen, не в стеке
                    )
                } else {
                    CheckedView(point = point, onContinue = { nav.pop() })
                }
            else -> {
                val scope = rememberCoroutineScope()
                CheckInView(
                    point = point,
                    loadingStage = (state as? QuestCheckInViewModel.State.Loading)?.stage,
                    error = (state as? QuestCheckInViewModel.State.Error)?.message,
                    onCheckIn = { qr -> vm.checkin(point, qrPayload = qr) },
                    // Q3 photo: снимок из галереи (как обложка в CreateEvent) → upload → чек-ин
                    onPickPhoto = {
                        scope.launch {
                            val file = FileKit.pickFile(type = PickerType.Image, mode = PickerMode.Single) ?: return@launch
                            val bytes = file.readBytes()
                            if (bytes.size > MAX_PHOTO_BYTES) {
                                vm.reset() // сбросим прошлую ошибку, покажем актуальную
                                photoTooBig = true
                                return@launch
                            }
                            photoTooBig = false
                            vm.checkinWithPhoto(point, bytes, imageContentType(file.name))
                        }
                    },
                    photoTooBig = photoTooBig,
                    onBack = { nav.pop() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Q3: экран чек-ина
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckInView(
    point: QuestPoint,
    loadingStage: String?,
    error: String?,
    onCheckIn: (qrPayload: String?) -> Unit,
    onPickPhoto: () -> Unit,
    photoTooBig: Boolean,
    onBack: () -> Unit,
) {
    val loading = loadingStage != null
    var qrCode by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(NightBackground)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Топ-бар
        Row(
            Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text(point.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
        }

        // Тип проверки — большая иллюстрация
        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Violet600.copy(alpha = 0.15f))
                .border(2.dp, Violet600.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(point.checkInType.emoji, fontSize = 56.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            when (point.checkInType) {
                CheckInType.Geo   -> "Ты на месте?"
                CheckInType.Photo -> "Сделай фото на месте"
                CheckInType.Qr    -> "Отсканируй QR на чеке"
            },
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            point.address,
            fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        // Условие
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(NightSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                when (point.checkInType) {
                    CheckInType.Geo   -> "📍 Геолокация · ≤ 300 м от точки"
                    CheckInType.Photo -> "📷 Гео-метка обязательна"
                    CheckInType.Qr    -> "🧾 Сканируй QR-код с чека"
                },
                fontSize = 12.sp, color = Violet400,
            )
        }

        Spacer(Modifier.weight(1f))

        // QR-условие: сканер камерой (Android/iOS); ручной ввод — фолбэк и для desktop
        if (point.checkInType == CheckInType.Qr) {
            if (qrScannerAvailable && !loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                ) {
                    QrScannerView(
                        onResult = { code -> onCheckIn(code) }, // распознали → сразу чек-ин
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "Наведи на QR на чеке",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            TextField(
                value = qrCode,
                onValueChange = { qrCode = it },
                placeholder = { Text("Или введи код вручную", color = NightTextSecondary, fontSize = 14.sp) },
                singleLine = true,
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
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Ошибка бэка (TOO_FAR / QR_INVALID / CHAIN_LOCKED / гео / загрузка фото)
        val shownError = if (photoTooBig) "Фото больше 8 МБ — выбери поменьше" else error
        if (shownError != null) {
            Text(
                shownError,
                color = Color(0xFFFF6B81), fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }

        // CTA
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple {
                    if (!loading) {
                        if (point.checkInType == CheckInType.Photo) onPickPhoto()
                        else onCheckIn(qrCode.trim().ifEmpty { null })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    loading -> loadingStage ?: "Проверяем…"
                    point.checkInType == CheckInType.Geo -> "✓ Я здесь"
                    point.checkInType == CheckInType.Photo -> "📷 Выбрать фото"
                    else -> "✓ Подтвердить код"
                },
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Промежуточное «Точка пройдена»
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckedView(point: QuestPoint, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(100.dp).clip(CircleShape).background(Soft.copy(alpha = 0.15f)).border(2.dp, Soft.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✓", fontSize = 44.sp, color = Soft, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.height(20.dp))
        Text("Точка пройдена!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(point.title, fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onContinue),
            contentAlignment = Alignment.Center,
        ) { Text("Следующая точка →", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Q4: финиш
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestFinishView(quest: QuestItem, xpGained: Int, prizeQr: String?, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎉", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Квест пройден!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Ты ${quest.finishersCount + 1}-й финишёр", fontSize = 14.sp, color = NightTextSecondary)

        Spacer(Modifier.height(28.dp))

        // QR-приз
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Soft.copy(alpha = 0.10f))
                .border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🎁", fontSize = 36.sp)
            Text(quest.rewardLabel, fontSize = 18.sp, color = Soft, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            // QR-код приза с бэка (prize_qr) — показываем кассиру
            Box(
                Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    prizeQr?.take(8) ?: "QR",
                    fontSize = if (prizeQr != null) 11.sp else 14.sp,
                    color = Color.Black, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
            Text(if (xpGained > 0) "🏅 +$xpGained XP · действует 24 часа" else "Действует 24 часа", fontSize = 11.sp, color = NightTextSecondary)
        }

        Spacer(Modifier.height(24.dp))

        // Значок-ачивка
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Violet600.copy(alpha = 0.18f))
                .border(1.dp, Violet400.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏅", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text("Ачивка добавлена в профиль", fontSize = 13.sp, color = Violet400, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { },
                contentAlignment = Alignment.Center,
            ) { Text("↗ Поделиться", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple(onClose),
                contentAlignment = Alignment.Center,
            ) { Text("На главную", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Фото-чекин: лимит и content-type (как в CreateEventScreen)
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_PHOTO_BYTES = 8 * 1024 * 1024

private fun imageContentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
