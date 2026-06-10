package com.eventmap.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri

actual class FileOpener(
    private val context: Context,
) {
    actual fun open(localUri: String, mimeType: String?) {
        val uri = Uri.parse(localUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "application/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // DownloadManager сохраняет в Downloads через MediaStore, поэтому
        // Uri уже content:// / file:// — FileProvider не нужен.
        runCatching { context.startActivity(intent) }
    }
}
