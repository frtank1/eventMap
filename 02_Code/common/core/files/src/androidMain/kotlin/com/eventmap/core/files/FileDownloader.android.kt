package com.eventmap.core.files

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.eventmap.core.datastore.TokenStorage

actual class FileDownloaderFactory(
    private val context: Context,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
) {
    actual fun create(): FileDownloader =
        AndroidFileDownloader(
            context = context.applicationContext,
            tokenStorage = tokenStorage,
            baseUrl = baseUrl,
        )
}

private class AndroidFileDownloader(
    private val context: Context,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
) : FileDownloader {

    override fun download(
        url: String,
        fileName: String,
        mimeType: String?,
    ): Flow<DownloadProgress> = callbackFlow {
        val dm = context.getSystemService<DownloadManager>()
            ?: run {
                trySend(DownloadProgress.Failure("DownloadManager недоступен"))
                close()
                return@callbackFlow
            }

        val absoluteUrl =
            if (url.startsWith("http")) url
            else baseUrl.trimEnd('/') + "/" + url.trimStart('/')

        val accessToken = tokenStorage.getActiveAccessToken()

        val request = DownloadManager.Request(Uri.parse(absoluteUrl))
            .setTitle(fileName)
            .setMimeType(mimeType ?: "application/octet-stream")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            // setDestinationInExternalFilesDir не требует
            // WRITE_EXTERNAL_STORAGE permission на любом API (приложение
            // пишет в свою песочницу). На Android 10+ это эквивалентно
            // MediaStore.Downloads без рантайм-разрешений.
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName,
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        if (accessToken.isNotBlank()) {
            request.addRequestHeader("Authorization", "Bearer $accessToken")
        }

        val id = try {
            dm.enqueue(request)
        } catch (t: Throwable) {
            trySend(
                DownloadProgress.Failure(
                    t.message ?: "Не удалось поставить файл на скачивание"
                )
            )
            close()
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())

        val poll = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(id)
                dm.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx =
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val totalIdx =
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val readIdx =
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val uriIdx =
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                        val reasonIdx =
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)

                        val status = cursor.getInt(statusIdx)
                        val downloaded = cursor.getLong(readIdx)
                        val total = cursor.getLong(totalIdx).takeIf { it > 0 }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val localUri = cursor.getString(uriIdx).orEmpty()
                                trySend(
                                    DownloadProgress.Success(
                                        localPath = localUri,
                                        localUri = localUri,
                                        mimeType = mimeType,
                                    )
                                )
                                close()
                                return
                            }

                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(reasonIdx)
                                trySend(
                                    DownloadProgress.Failure(
                                        "Ошибка скачивания (код $reason)"
                                    )
                                )
                                close()
                                return
                            }

                            else -> {
                                trySend(
                                    DownloadProgress.InProgress(
                                        bytesRead = downloaded,
                                        totalBytes = total,
                                    )
                                )
                            }
                        }
                    }
                }
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        handler.post(poll)

        awaitClose {
            handler.removeCallbacks(poll)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
    }
}
