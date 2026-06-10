package com.eventmap.core.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.eventmap.core.datastore.TokenStorage
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDomainMask
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue

actual class FileDownloaderFactory(
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
) {
    actual fun create(): FileDownloader = IosFileDownloader(tokenStorage, baseUrl)
}

@OptIn(ExperimentalForeignApi::class)
private class IosFileDownloader(
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
) : FileDownloader {

    override fun download(
        url: String,
        fileName: String,
        mimeType: String?,
    ): Flow<DownloadProgress> = callbackFlow {
        val absolute =
            if (url.startsWith("http")) url
            else baseUrl.trimEnd('/') + "/" + url.trimStart('/')

        val nsUrl = NSURL(string = absolute)
        val request = NSMutableURLRequest(uRL = nsUrl)
        val accessToken = tokenStorage.getActiveAccessToken()
        if (accessToken.isNotBlank()) {
            request.setValue(
                "Bearer $accessToken",
                forHTTPHeaderField = "Authorization",
            )
        }

        trySend(DownloadProgress.InProgress(bytesRead = 0L, totalBytes = null))

        val task = NSURLSession.sharedSession.downloadTaskWithRequest(request) {
                location: NSURL?, response: NSURLResponse?, error: NSError? ->
            when {
                error != null -> {
                    trySend(
                        DownloadProgress.Failure(
                            error.localizedDescription
                        )
                    )
                }

                location != null -> {
                    val docs = (NSSearchPathForDirectoriesInDomains(
                        NSDocumentDirectory,
                        NSUserDomainMask,
                        true,
                    ).first() as? String).orEmpty()
                    val dest = NSURL.fileURLWithPath("$docs/$fileName")

                    val fm = NSFileManager.defaultManager
                    fm.removeItemAtURL(dest, null)
                    val moved = fm.moveItemAtURL(location, dest, null)

                    if (moved) {
                        trySend(
                            DownloadProgress.Success(
                                localPath = dest.path.orEmpty(),
                                localUri = dest.absoluteString.orEmpty(),
                                mimeType = mimeType,
                            )
                        )
                    } else {
                        trySend(
                            DownloadProgress.Failure(
                                "Не удалось сохранить файл"
                            )
                        )
                    }
                }

                else -> {
                    trySend(
                        DownloadProgress.Failure("Пустой ответ от сервера")
                    )
                }
            }
            close()
        }

        task.resume()

        awaitClose {
            task.cancel()
        }
    }
}
