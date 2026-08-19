package com.eventmap.views.qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

actual val qrScannerAvailable: Boolean = true

// AVCaptureMetadataOutput: системное распознавание QR без сторонних библиотек.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScannerView(onResult: (String) -> Unit, modifier: Modifier) {
    val scanner = remember { IosQrScanner(onResult) }
    UIKitView(
        factory = { QrPreviewView(scanner.session) },
        modifier = modifier,
    )
    DisposableEffect(scanner) { onDispose { scanner.release() } }
}

@OptIn(ExperimentalForeignApi::class)
private class IosQrScanner(private val onResult: (String) -> Unit) {

    val session = AVCaptureSession()
    private var delivered = false

    // AVFoundation держит delegate weak — ретейним сами
    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: platform.AVFoundation.AVCaptureConnection,
        ) {
            if (delivered) return
            val code = didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstNotNullOfOrNull { it.stringValue }
                ?: return
            delivered = true
            onResult(code)
        }
    }

    init {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            if (granted) configureAndStart()
        }
    }

    private fun configureAndStart() {
        session.beginConfiguration()
        (AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo))?.let { device ->
            AVCaptureDeviceInput.deviceInputWithDevice(device, null)?.let { input ->
                if (session.canAddInput(input)) session.addInput(input)
            }
        }
        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            // тип задаётся ПОСЛЕ addOutput, делегат — на main queue
            output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
            // симулятор без камеры не умеет QR — без guard'а упадём NSInvalidArgumentException
            if (output.availableMetadataObjectTypes.contains(AVMetadataObjectTypeQRCode)) {
                output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }
        }
        session.commitConfiguration()
        dispatch_async(dispatch_get_global_queue(0, 0u)) {
            if (!session.running) session.startRunning()
        }
    }

    fun release() {
        if (session.running) session.stopRunning()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class QrPreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {

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
