package com.eventmap.core.files

import kotlinx.coroutines.flow.Flow

/**
 * Кроссплатформенный загрузчик файлов с авторизацией Bearer токеном
 * текущей сессии (берётся из `TokenStorage`).
 *
 * На Android реализован через системный `DownloadManager` —
 * файл сохраняется в Downloads, показывается уведомление с прогрессом,
 * никаких рантайм-разрешений на Android 10+ не нужно.
 *
 * На iOS — через `NSURLSession.downloadTask`, файл кладётся
 * в Documents директорию приложения; затем UI открывает share-sheet.
 */
interface FileDownloader {

    /**
     * Запускает скачивание.
     *
     * @param url абсолютный URL или относительный путь к API.
     * @param fileName имя файла, под которым сохранить.
     * @param mimeType MIME-тип (помогает системе понять, как открыть файл).
     */
    fun download(
        url: String,
        fileName: String,
        mimeType: String? = null,
    ): Flow<DownloadProgress>
}

sealed class DownloadProgress {
    /** Запрос поставлен в очередь / выполняется. `total` может быть null если сервер не отдал Content-Length. */
    data class InProgress(
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : DownloadProgress()

    /** Файл скачан. `localUri` можно передать в системный viewer или share-sheet. */
    data class Success(
        val localPath: String,
        val localUri: String,
        val mimeType: String?,
    ) : DownloadProgress()

    /** Ошибка скачивания. */
    data class Failure(val message: String) : DownloadProgress()
}
