package com.eventmap.core.platform.share

import PlatformConfiguration
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun createShareService(configuration: PlatformConfiguration): ShareService = IosShareService()

private class IosShareService : ShareService {

    override fun shareText(text: String) {
        dispatch_async(dispatch_get_main_queue()) {
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@dispatch_async
            val controller = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            topmost(root).presentViewController(controller, animated = true, completion = null)
        }
    }

    // презентуем с самого верхнего VC — иначе share не покажется поверх модалок
    private fun topmost(root: UIViewController): UIViewController {
        var top = root
        while (top.presentedViewController != null) top = top.presentedViewController!!
        return top
    }
}
