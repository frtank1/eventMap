package com.eventmap.views.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Wasm: MediaRecorder API браузера — позже; пока рекордер недоступен (демо-режим)
@Composable
actual fun rememberCameraRecorder(): CameraRecorder = remember { StubCameraRecorder }

@Composable
actual fun CameraPreview(recorder: CameraRecorder, modifier: Modifier) {
    Box(modifier.background(Color(0xFF050510)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 44.sp)
            Text("Камера — в мобильном приложении", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
        }
    }
}

internal object StubCameraRecorder : CameraRecorder {
    override val available: Boolean = false
    override fun setFrontCamera(front: Boolean) = Unit
    override fun setTorch(on: Boolean) = Unit
    override fun startRecording() = Unit
    override suspend fun stopRecording(): RecordedVideo? = null
    override fun release() = Unit
}
