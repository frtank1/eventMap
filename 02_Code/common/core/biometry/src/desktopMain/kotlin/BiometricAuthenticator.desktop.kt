package com.eventmap.core.biometry

actual class BiometricAuthenticator {
    actual fun isAvailable(): Boolean = false
    actual suspend fun authenticate(reason: String): BiometricResult = BiometricResult.NotAvailable
}
