package com.eventmap.core.platform.share

import PlatformConfiguration
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// Desktop: системного share-шита нет — копируем текст в буфер обмена.
actual fun createShareService(configuration: PlatformConfiguration): ShareService =
    object : ShareService {
        override fun shareText(text: String) {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            }
        }
    }
