package com.eventmap.views.qr

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual val qrScannerAvailable: Boolean = true

// CameraX Preview + ImageAnalysis → ZXing. Первый успешный декод → onResult, анализ выключается.
@Composable
actual fun QrScannerView(onResult: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { QrAnalyzer(onResult) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                scanner.attach(this) // паттерн CameraRecorder: factory до LaunchedEffect
            }
        },
        modifier = modifier,
    )

    LaunchedEffect(scanner) { scanner.bind(context.applicationContext, lifecycleOwner) }
    DisposableEffect(scanner) { onDispose { scanner.release() } }
}

private class QrAnalyzer(private val onResult: (String) -> Unit) {

    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var delivered = false

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    fun attach(view: PreviewView) { previewView = view }

    suspend fun bind(context: Context, lifecycleOwner: LifecycleOwner) {
        val view = previewView ?: return
        val cameraProvider = awaitProvider(context).also { provider = it }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context)) { image -> analyze(image) } }
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }

    private fun analyze(image: ImageProxy) {
        image.use { proxy ->
            if (delivered) return
            val text = decode(proxy.toBitmap()) ?: return
            delivered = true
            onResult(text)
        }
    }

    private fun decode(bitmap: Bitmap): String? = try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null // NotFoundException на большинстве кадров — норма
    } finally {
        reader.reset()
    }

    fun release() {
        runCatching { provider?.unbindAll() }
        provider = null
        previewView = null
    }

    private suspend fun awaitProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { if (cont.isActive) cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }
}
