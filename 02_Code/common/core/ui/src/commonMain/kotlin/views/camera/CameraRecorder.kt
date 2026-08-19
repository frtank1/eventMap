package com.eventmap.views.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// =====================================================================
//  Кроссплатформенная запись видео (рилсы E3).
//  Android — CameraX (Preview + VideoCapture), iOS — AVCaptureSession,
//  desktop/wasm — недоступно (available = false, экран показывает демо).
// =====================================================================

/**
 * Результат записи.
 * @param bytes байты файла (mp4/mov) — для publishReel (presign→PUT→confirm).
 * @param contentType "video/mp4" | "video/quicktime".
 * @param localUrl file://-ссылка на локальный файл — для превью в VideoPlayer.
 */
class RecordedVideo(
    val bytes: ByteArray,
    val contentType: String,
    val localUrl: String?,
)

interface CameraRecorder {
    /** false — на этой платформе камеры нет (desktop/wasm): экран работает в демо-режиме */
    val available: Boolean

    /** Передняя/задняя камера (перепривязывает сессию) */
    fun setFrontCamera(front: Boolean)

    /** Фонарик (best-effort: игнорируется, если камера не умеет) */
    fun setTorch(on: Boolean)

    /** Начать запись в локальный файл */
    fun startRecording()

    /** Остановить запись и дождаться файла. null — ошибка или запись не шла */
    suspend fun stopRecording(): RecordedVideo?

    /** Отпустить камеру (уход с экрана) */
    fun release()
}

/** Рекордер, живущий в композиции экрана записи. Разрешения запрашивает платформа. */
@Composable
expect fun rememberCameraRecorder(): CameraRecorder

/** Вьюфайндер камеры на всю площадь модификатора */
@Composable
expect fun CameraPreview(recorder: CameraRecorder, modifier: Modifier = Modifier)
