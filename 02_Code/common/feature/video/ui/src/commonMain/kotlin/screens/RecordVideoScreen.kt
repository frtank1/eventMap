package com.eventmap.video.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.LiveGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import com.eventmap.video.ui.viewmodel.RecordPhase
import com.eventmap.video.ui.viewmodel.RecordVideoViewModel
import com.eventmap.views.camera.CameraPreview
import com.eventmap.views.camera.CameraRecorder
import com.eventmap.views.camera.rememberCameraRecorder
import com.eventmap.views.video.VideoPlayer
import kotlinx.coroutines.launch

// =====================================================================
//  E3 — Запись рилса
//  E4 — Превью + публикация
// =====================================================================

/**
 * @param eventId привязка ролика к событию («Я тоже здесь · снять момент» из LIVE-шторки);
 *        null — общий момент города (кнопка 🎬 в ленте «Сейчас»).
 */
data class RecordVideoScreen(
    val eventId: String? = null,
    val eventTitle: String = "",
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<RecordVideoViewModel>()
        val state by vm.state.collectAsState()
        val recorder = rememberCameraRecorder()
        val scope = rememberCoroutineScope()

        fun stopAndPreview() {
            if (state.stopping) return
            vm.markStopping()
            scope.launch { vm.onRecorded(recorder.stopRecording()) } // null на desktop → демо
        }

        // Запись стартует сразу при входе (как в макете: REC уже идёт)
        LaunchedEffect(recorder) {
            if (state.phase == RecordPhase.Recording) recorder.startRecording()
        }
        // Автостоп на лимите 60 с
        LaunchedEffect(state.elapsedSec) {
            if (state.phase == RecordPhase.Recording && vm.reachedLimit()) stopAndPreview()
        }

        when (state.phase) {
            RecordPhase.Recording -> RecordingView(
                recorder = recorder,
                progress = state.progress,
                elapsed = state.elapsedSec,
                stopping = state.stopping,
                onStop = { stopAndPreview() },
                onFlip = {
                    vm.flipCamera()
                    recorder.setFrontCamera(!state.frontCamera)
                },
                onFlash = {
                    vm.toggleFlash()
                    recorder.setTorch(!state.flashOn)
                },
                onCancel = {
                    recorder.release()
                    nav.pop()
                },
            )
            RecordPhase.Preview -> PreviewView(
                previewUrl = vm.previewUrl(),
                caption = state.caption,
                eventTitle = eventTitle, // контекст-пилюля «Из события · …»
                loading = state.publishing,
                errorText = state.errorText,
                onCaptionChange = { vm.setCaption(it) },
                onPublish = { vm.publish(eventId) { nav.pop() } },
                onDiscard = {
                    vm.discard()
                    nav.pop()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  E3: экран записи
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingView(
    recorder: CameraRecorder,
    progress: Float,
    elapsed: Int,
    stopping: Boolean,
    onStop: () -> Unit,
    onFlip: () -> Unit,
    onFlash: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // Вьюфайндер камеры (desktop/wasm — заглушка внутри CameraPreview)
        CameraPreview(recorder = recorder, modifier = Modifier.fillMaxSize())

        // Верхняя панель
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Закрыть
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickableNoRipple(onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Таймер-пилюля
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF4757)))
                Spacer(Modifier.width(6.dp))
                Text(
                    formatTime(elapsed),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
            }

            // Флэш
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickableNoRipple(onFlash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FlashOn, contentDescription = "Вспышка", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Прогресс-бар записи (макс 60 с)
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).padding(top = 88.dp),
            color = Color(0xFFFF4757),
            trackColor = Color.White.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
        )

        // Нижняя панель: перевернуть + шаттер
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Перевернуть камеру
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                    .clickableNoRipple(onFlip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Cameraswitch, contentDescription = "Перевернуть", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Шаттер — большая красная кнопка «Стоп»
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(LiveGradient))
                    .clickableNoRipple { if (!stopping) onStop() },
                contentAlignment = Alignment.Center,
            ) {
                if (stopping) {
                    Text("…", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
                }
            }

            // Плейсхолдер для симметрии
            Spacer(Modifier.size(48.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  E4: превью + публикация
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewView(
    previewUrl: String?,
    caption: String,
    eventTitle: String,
    loading: Boolean,
    errorText: String?,
    onCaptionChange: (String) -> Unit,
    onPublish: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NightBackground),
    ) {
        // Превью снятого ролика (file://); нет файла (демо) — плейсхолдер
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0D0818), Color(0xFF1A0D2E))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (previewUrl != null) {
                VideoPlayer(url = previewUrl, playing = true, modifier = Modifier.fillMaxSize())
            } else {
                Text("▶", fontSize = 64.sp, color = Color.White.copy(alpha = 0.15f))
            }

            // Контекст-пилюля события
            if (eventTitle.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Violet600.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📍", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Из события · $eventTitle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Панель подписи и кнопок
        Column(
            Modifier
                .fillMaxWidth()
                .background(NightSurface)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("ПОДПИСЬ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

            // Поле подписи
            TextField(
                value = caption,
                onValueChange = onCaptionChange,
                placeholder = { Text("Расскажи что происходит…", color = NightTextSecondary, fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Violet400,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            )

            if (errorText != null) {
                Text(errorText, color = Color(0xFFFF6B81), fontSize = 12.sp)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Удалить
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickableNoRipple(onDiscard),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Удалить", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                // Опубликовать
                Box(
                    Modifier
                        .weight(2f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (loading) SolidColor(Color.White.copy(alpha = 0.12f))
                            else Brush.linearGradient(BrandGradient),
                        )
                        .clickableNoRipple { if (!loading) onPublish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (loading) "Публикуем…" else "Опубликовать",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

private fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Вспомогательные типы (заглушки, до реализации ViewModel)
// ─────────────────────────────────────────────────────────────────────────────

// RecordPhase определён в com.eventmap.video.ui.viewmodel.RecordVideoViewModel
