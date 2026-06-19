package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.repository.DeviceTokenRepository
import com.eventmap.server.domain.repository.NotificationPort

// ===== Push-уведомления (FCM/APNS) =====

class RegisterPushTokenUseCase(private val tokens: DeviceTokenRepository) {
    suspend operator fun invoke(userId: String, token: String, platform: String) {
        tokens.register(userId, token, platform.ifBlank { "android" })
    }
}

/** Отправка пуша пользователю. @return число успешных доставок (0 — если push выключен/нет устройств). */
class SendPushUseCase(private val notifications: NotificationPort) {
    suspend operator fun invoke(userId: String, title: String, body: String): Int =
        notifications.sendToUser(userId, title, body)
}
