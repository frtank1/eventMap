package com.eventmap.core.biometry

import PlatformConfiguration
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// App выставляет текущую активити (например в onResume MainActivity),
// т.к. BiometricPrompt требует FragmentActivity, а не Context.
object BiometricActivityHolder {
    var activity: FragmentActivity? = null
}

actual class BiometricAuthenticator(
    private val configuration: PlatformConfiguration
) {
    private val context: Context get() = configuration.androidContext

    actual fun isAvailable(): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun authenticate(reason: String): BiometricResult {
        val activity = BiometricActivityHolder.activity ?: return BiometricResult.NotAvailable
        if (!isAvailable()) return BiometricResult.NotAvailable

        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(BiometricResult.Success)
                    }

                    override fun onAuthenticationFailed() {
                        if (cont.isActive) cont.resume(BiometricResult.Failed)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(BiometricResult.Error(errString.toString()))
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        }
    }
}
