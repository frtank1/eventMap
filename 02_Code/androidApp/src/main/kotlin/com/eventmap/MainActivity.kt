package com.eventmap

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import io.github.vinceglb.filekit.core.FileKit
import com.eventmap.core.biometry.BiometricActivityHolder

// FragmentActivity (а не ComponentActivity) — этого требует BiometricPrompt
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        requestLocationPermissionIfNeeded() // гео (Q3/D1c) + камера и микрофон (рилсы E3)
        setContent {
            App()
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
        val camera = android.Manifest.permission.CAMERA
        val audio = android.Manifest.permission.RECORD_AUDIO
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
        val wanted = buildList {
            addAll(listOf(fine, coarse, camera, audio))
            // напоминания «за час до события» (Android 13+ требует runtime-разрешение)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { checkSelfPermission(it) != granted }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    override fun onResume() {
        super.onResume()
        BiometricActivityHolder.activity = this
    }

    override fun onPause() {
        super.onPause()
        if (BiometricActivityHolder.activity === this) {
            BiometricActivityHolder.activity = null
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}