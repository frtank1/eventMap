package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import java.time.Instant

/** Shared by create and update so editing cannot bypass publication constraints. */
internal fun Event.validationError(nowMs: Long = System.currentTimeMillis()): String? {
    if (kind !in setOf("planned", "gathering", "live_signal", "private")) return "Неизвестный тип события"
    if (visibility !in setOf("public", "unlisted", "invite")) return "Неизвестный режим видимости"
    if (joinPolicy !in setOf("open", "approval", "invite")) return "Неизвестный режим вступления"
    if (audience !in setOf("everyone", "family", "adults")) return "Неизвестная аудитория"
    if (title.isBlank() || title.length > 200) return "Название: от 1 до 200 символов"
    if (category.isBlank()) return "Категория обязательна"
    if (!location.lat.isFinite() || !location.lng.isFinite() ||
        location.lat !in -90.0..90.0 || location.lng !in -180.0..180.0 || location.name.isBlank()
    ) return "Укажите корректную локацию"
    val start = runCatching { Instant.parse(startAt) }.getOrNull() ?: return "Укажите время начала с часовым поясом"
    if (endAt.isNotBlank()) {
        val end = runCatching { Instant.parse(endAt) }.getOrNull() ?: return "Укажите корректное время окончания"
        if (end <= start) return "Окончание должно быть позже начала"
    }
    if (price.type !in setOf("free", "paid") || price.amount < 0 ||
        (price.type == "paid" && price.amount == 0) || (price.type == "free" && price.amount != 0)
    ) return "Некорректная цена"
    if (capacity != null && capacity !in 2..100_000) return "Некорректный лимит участников"
    if (kind == "gathering" && capacity == null) return "Для набора людей укажите лимит участников"
    if (minAge != null && minAge !in 0..120) return "Некорректный возраст"
    if (audience == "adults" && (minAge ?: 0) < 18) return "Для взрослой аудитории возраст должен быть не менее 18 лет"
    if (kind == "live_signal" && (expiresAtMs == null || expiresAtMs <= nowMs || expiresAtMs > nowMs + 86_400_000L)) {
        return "Срок живого сигнала должен быть в пределах следующих 24 часов"
    }
    return null
}
