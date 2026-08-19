package com.eventmap.views.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// =====================================================================
//  Кроссплатформенный QR-сканер (чек-ин покупок в квестах Q3).
//  Android — CameraX ImageAnalysis + ZXing, iOS — AVCaptureMetadataOutput,
//  desktop/wasm — заглушка (остаётся ручной ввод кода).
//  onResult вызывается ОДИН раз первым распознанным кодом.
// =====================================================================

/** true — на платформе есть сканер (показывать вьюфайндер вместо ручного ввода) */
expect val qrScannerAvailable: Boolean

@Composable
expect fun QrScannerView(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
)
