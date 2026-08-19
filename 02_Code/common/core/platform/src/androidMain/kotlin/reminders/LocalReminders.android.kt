package com.eventmap.core.platform.reminders

import PlatformConfiguration
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// WorkManager вместо AlarmManager: не требует SCHEDULE_EXACT_ALARM,
// переживает перезапуск процесса. Точность ±несколько минут — для «за час» достаточно.
actual fun createLocalReminders(configuration: PlatformConfiguration): LocalReminders =
    AndroidLocalReminders(configuration.androidContext.applicationContext)

private const val CHANNEL_ID = "event_reminders"

private class AndroidLocalReminders(private val context: Context) : LocalReminders {

    override fun schedule(id: String, title: String, body: String, atEpochMs: Long) {
        val delayMs = atEpochMs - System.currentTimeMillis()
        if (delayMs <= 0) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString("title", title)
                    .putString("body", body)
                    .putInt("notifId", id.hashCode())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(id)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(inputData.getString("title") ?: "Скоро событие")
            .setContentText(inputData.getString("body") ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(inputData.getString("body") ?: ""))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            // без POST_NOTIFICATIONS (Android 13+) система бросит SecurityException — глотаем
            NotificationManagerCompat.from(applicationContext)
                .notify(inputData.getInt("notifId", 0), notification)
        }
        return Result.success()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Напоминания о событиях", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
