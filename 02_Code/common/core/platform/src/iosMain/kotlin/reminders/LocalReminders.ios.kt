package com.eventmap.core.platform.reminders

import PlatformConfiguration
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun createLocalReminders(configuration: PlatformConfiguration): LocalReminders = IosLocalReminders()

private class IosLocalReminders : LocalReminders {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        // Разрешение спрашиваем заранее — диалог покажется один раз
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    override fun schedule(id: String, title: String, body: String, atEpochMs: Long) {
        val intervalSec = (atEpochMs - nowMs()) / 1000.0
        if (intervalSec <= 1.0) return
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(intervalSec, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun cancel(id: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }

    private fun nowMs(): Long =
        (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()
}
