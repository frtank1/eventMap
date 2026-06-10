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
        setContent {
            App()
        }
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