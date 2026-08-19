package com.eventmap.views.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.hasTorch
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.posix.memcpy

// AVCaptureSession + AVCaptureMovieFileOutput → .mov во временный файл.
// Разрешения: NSCameraUsageDescription / NSMicrophoneUsageDescription (Info.plist);
// запрашиваются при первом входе на экран записи.
@Composable
actual fun rememberCameraRecorder(): CameraRecorder {
    val recorder = remember { IosCameraRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    return recorder
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(recorder: CameraRecorder, modifier: Modifier) {
    val ios = recorder as? IosCameraRecorder ?: return
    UIKitView(
        factory = { CameraPreviewView(ios.session) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
internal class IosCameraRecorder : CameraRecorder {

    val session = AVCaptureSession()
    private val output = AVCaptureMovieFileOutput()
    private var finished: CompletableDeferred<RecordedVideo?>? = null
    private var delegate: RecordDelegate? = null // AVFoundation держит weak — ретейним сами
    private var front = false
    private var recordingActive = false

    override val available: Boolean = true

    init {
        // доступ к камере/микрофону: системный диалог при первом использовании
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            if (granted) {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { _ ->
                    configureAndStart()
                }
            }
        }
    }

    private fun configureAndStart() {
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh
        attachInputs()
        if (!session.outputs.contains(output) && session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()
        // startRunning блокирует — уводим с main-потока
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(0, 0u)) {
            if (!session.running) session.startRunning()
        }
    }

    private fun attachInputs() {
        // убрать старые входы (смена камеры)
        session.inputs.filterIsInstance<AVCaptureDeviceInput>().forEach { session.removeInput(it) }

        val position = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        )
        (discovery.devices.firstOrNull() as? AVCaptureDevice)?.let { device ->
            AVCaptureDeviceInput.deviceInputWithDevice(device, null)?.let { input ->
                if (session.canAddInput(input)) session.addInput(input)
            }
        }
        (AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio))?.let { mic ->
            AVCaptureDeviceInput.deviceInputWithDevice(mic, null)?.let { input ->
                if (session.canAddInput(input)) session.addInput(input)
            }
        }
    }

    override fun setFrontCamera(newFront: Boolean) {
        if (front == newFront) return
        front = newFront
        session.beginConfiguration()
        attachInputs()
        session.commitConfiguration()
    }

    override fun setTorch(on: Boolean) {
        val device = session.inputs.filterIsInstance<AVCaptureDeviceInput>()
            .map { it.device }
            .firstOrNull { it.hasTorch }
            ?: return
        runCatching {
            device.lockForConfiguration(null)
            device.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            device.unlockForConfiguration()
        }
    }

    override fun startRecording() {
        if (recordingActive) return
        recordingActive = true
        val result = CompletableDeferred<RecordedVideo?>()
        finished = result
        val path = NSTemporaryDirectory() + "reel-${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.mov"
        val url = NSURL.fileURLWithPath(path)
        val recDelegate = RecordDelegate { fileUrl, error ->
            recordingActive = false
            val video = if (error == null && fileUrl != null) {
                NSData.dataWithContentsOfURL(fileUrl)?.let { data ->
                    RecordedVideo(
                        bytes = data.toByteArray(),
                        contentType = "video/quicktime",
                        localUrl = fileUrl.absoluteString,
                    )
                }
            } else {
                null
            }
            result.complete(video)
        }
        delegate = recDelegate
        output.startRecordingToOutputFileURL(url, recordingDelegate = recDelegate)
    }

    override suspend fun stopRecording(): RecordedVideo? {
        if (!recordingActive) return null
        val result = finished ?: return null
        output.stopRecording()
        return result.await()
    }

    override fun release() {
        runCatching { if (recordingActive) output.stopRecording() }
        recordingActive = false
        if (session.running) session.stopRunning()
    }
}

// Делегат окончания записи (retained в IosCameraRecorder.delegate)
private class RecordDelegate(
    private val onFinish: (NSURL?, NSError?) -> Unit,
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        onFinish(didFinishRecordingToOutputFileAtURL, error)
    }
}

// Вьюфайндер: AVCaptureVideoPreviewLayer на всю площадь
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}
