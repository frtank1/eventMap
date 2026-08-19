package com.eventmap.views.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

// CameraX: превью + VideoCapture(Recorder) в кэш-файл. Разрешение CAMERA/RECORD_AUDIO
// запрашивает MainActivity при старте; без CAMERA available=false (экран уйдёт в демо).
@Composable
actual fun rememberCameraRecorder(): CameraRecorder {
    val context = LocalContext.current
    val recorder = remember { AndroidCameraRecorder(context.applicationContext) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    return recorder
}

@Composable
actual fun CameraPreview(recorder: CameraRecorder, modifier: Modifier) {
    val android = recorder as? AndroidCameraRecorder ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                android.attachPreview(this)
            }
        },
        modifier = modifier,
    )
    // (пере)привязка при создании и смене камеры
    LaunchedEffect(android, android.frontVersion) { android.bind(lifecycleOwner) }
}

internal class AndroidCameraRecorder(private val context: Context) : CameraRecorder {

    override val available: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private var previewView: PreviewView? = null
    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var recording: Recording? = null
    private var finished: CompletableDeferred<RecordedVideo?>? = null
    private var front = false
    private var torch = false

    // меняется при flip — триггерит перепривязку в CameraPreview
    var frontVersion by mutableStateOf(0)
        private set

    fun attachPreview(view: PreviewView) { previewView = view }

    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        if (!available) return
        val view = previewView ?: return
        val provider = awaitProvider().also { this.provider = it }

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val capture = VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build(),
        )
        videoCapture = capture

        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            camera?.cameraControl?.enableTorch(torch)
        }
    }

    override fun setFrontCamera(newFront: Boolean) {
        if (front == newFront) return
        front = newFront
        frontVersion++
    }

    override fun setTorch(on: Boolean) {
        torch = on
        camera?.cameraControl?.enableTorch(on)
    }

    override fun startRecording() {
        if (!available || recording != null) return
        val capture = videoCapture ?: return
        val file = File(context.cacheDir, "reel-${System.currentTimeMillis()}.mp4")
        val result = CompletableDeferred<RecordedVideo?>()
        finished = result

        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply {
                // звук — только с выданным разрешением, иначе SecurityException
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    @Suppress("MissingPermission")
                    withAudioEnabled()
                }
            }

        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                val video = if (!event.hasError() && file.exists()) {
                    RecordedVideo(
                        bytes = file.readBytes(),
                        contentType = "video/mp4",
                        localUrl = "file://${file.absolutePath}",
                    )
                } else {
                    null
                }
                result.complete(video)
            }
        }
    }

    override suspend fun stopRecording(): RecordedVideo? {
        val active = recording ?: return null
        val result = finished ?: return null
        active.stop()
        return result.await()
    }

    override fun release() {
        runCatching { recording?.stop() }
        recording = null
        runCatching { provider?.unbindAll() }
        provider = null
        camera = null
        previewView = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { if (cont.isActive) cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }
}
