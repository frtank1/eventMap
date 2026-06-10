package com.eventmap.core.biometry

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual class BiometricAuthenticator {

    actual fun isAvailable(): Boolean {
        return LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            null
        )
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) {
            return BiometricResult.NotAvailable
        }
        return suspendCancellableCoroutine { cont ->
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = reason
            ) { success, error ->
                when {
                    success -> cont.resume(BiometricResult.Success)
                    error != null -> cont.resume(BiometricResult.Error(error.localizedDescription ?: "Error"))
                    else -> cont.resume(BiometricResult.Failed)
                }
            }
        }
    }
}
