package com.eventmap.core.biometry

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Failed : BiometricResult
    data object NotAvailable : BiometricResult   // нет железа: desktop, web
    data class Error(val message: String) : BiometricResult
}

expect class BiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(reason: String): BiometricResult
}
