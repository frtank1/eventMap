package com.eventmap.core.files

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
actual class FileOpener {
    actual fun open(localUri: String, mimeType: String?) {
        val url = NSURL(string = localUri) ?: return

        val controller = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )

        val rootController = UIApplication.sharedApplication
            .keyWindow
            ?.rootViewController
            ?: return

        // Если уже что-то презентовано — поднимаемся вверх по иерархии.
        var top = rootController
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }

        top.presentViewController(
            controller,
            animated = true,
            completion = null,
        )
    }
}
