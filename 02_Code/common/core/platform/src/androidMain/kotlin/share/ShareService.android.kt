package com.eventmap.core.platform.share

import PlatformConfiguration
import android.content.Intent

actual fun createShareService(configuration: PlatformConfiguration): ShareService =
    AndroidShareService(configuration)

private class AndroidShareService(private val configuration: PlatformConfiguration) : ShareService {

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // стартуем из applicationContext → нужен NEW_TASK
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { configuration.androidContext.startActivity(chooser) }
    }
}
