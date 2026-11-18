package com.eventmap.server.domain.usecase

import com.eventmap.server.domain.model.Event
import com.eventmap.server.domain.model.EventArea
import com.eventmap.server.domain.model.EventRecurrence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One vocabulary for every channel: the app, the web suggestion form, editorial import and the admin queue. */
object EventVocabulary {
    val KINDS = setOf("planned", "gathering", "live_signal", "private")
    val VISIBILITIES = setOf("public", "unlisted", "invite")
    val JOIN_POLICIES = setOf("open", "approval", "invite")
    val AUDIENCES = setOf("everyone", "family", "adults")
    /** editorial = curated pick with a source link; guest = suggestion without an account. */
    val SOURCE_TYPES = setOf("organizer", "business", "government", "initiator", "witness", "editorial", "guest")
    val MODERATION = setOf("pending", "published", "rejected", "expired")
    val PRESENTATIONS = setOf("S", "M", "L")
    /** Scene templates the 3D city can draw for level L. Parameters are data for the template, never code. */
    val SCENE_TEMPLATES = setOf("concert", "fair", "run", "mall_sale")
    const val SCENE_PARAMS_LIMIT = 8 * 1024
    const val MAX_AREA_POINTS = 200
    const val MAX_ENTRANCES = 20
}

/** Astana is UTC+5 all year; recurrence dates are Astana calendar dates. */
internal val ASTANA_ZONE: ZoneOffset = ZoneOffset.ofHours(5)

private fun String.hasControl() = any { (it.code < 32 && it != '\n' && it != '\t') || it.code == 127 }
private fun inRange(lng: Double, lat: Double) =
    lng.isFinite() && lat.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

/** Shared by create, update, suggestion and import so no channel can bypass publication constraints. */
internal fun Event.validationError(nowMs: Long = System.currentTimeMillis()): String? {
    if (kind !in EventVocabulary.KINDS) return "Неизвестный тип события"
    if (visibility !in EventVocabulary.VISIBILITIES) return "Неизвестный режим видимости"
    if (joinPolicy !in EventVocabulary.JOIN_POLICIES) return "Неизвестный режим вступления"
    if (audience !in EventVocabulary.AUDIENCES) return "Неизвестная аудитория"
    if (sourceType !in EventVocabulary.SOURCE_TYPES) return "Неизвестный источник события"
    if (moderationStatus !in EventVocabulary.MODERATION) return "Неизвестный статус модерации"
    if (title.isBlank() || title.length > 200) return "Название: от 1 до 200 символов"
    if (description.length > 10_000) return "Описание: не больше 10 000 символов"
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
    recurrence?.let { rule -> recurrenceError(rule, start)?.let { return it } }
    area?.let { a -> areaError(a)?.let { return it } }
    presentationError()?.let { return it }
    sourceError()?.let { return it }
    if (contact != null && (contact.isBlank() || contact.length > 200 || contact.hasControl())) return "Контакт: до 200 символов"
    if (externalId != null && !Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}").matches(externalId)) return "Некорректный внешний идентификатор"
    return null
}

private fun Event.recurrenceError(rule: EventRecurrence, start: Instant): String? {
    if (kind != "planned") return "Повтор доступен только для событий по расписанию"
    if (rule.weekdays.isEmpty() || rule.weekdays.toSet().size != rule.weekdays.size || rule.weekdays.any { it !in 1..7 })
        return "Повтор: дни недели от 1 (пн) до 7 (вс) без повторов"
    val until = runCatching { LocalDate.parse(rule.until) }.getOrNull() ?: return "Повтор: дата окончания в формате ГГГГ-ММ-ДД"
    val first = start.atOffset(ASTANA_ZONE).toLocalDate()
    if (until < first || until > first.plusDays(366)) return "Повтор: дата окончания — в течение года после начала"
    return null
}

private fun Event.areaError(area: EventArea): String? {
    val points = area.polygon
    if (points.size !in 3..EventVocabulary.MAX_AREA_POINTS) return "Территория: от 3 до ${EventVocabulary.MAX_AREA_POINTS} точек"
    if (points.any { it.size != 2 || !inRange(it[0], it[1]) }) return "Территория: точки в формате [долгота, широта]"
    val lngs = points.map { it[0] }
    val lats = points.map { it[1] }
    // A territory is a park, a square or a stadium, not a district: about 5 km at most.
    if (lats.max() - lats.min() > 0.05 || lngs.max() - lngs.min() > 0.08) return "Территория слишком большая"
    if (lats.max() - lats.min() < 0.00005 && lngs.max() - lngs.min() < 0.00005) return "Территория слишком маленькая"
    val pad = 0.005
    fun near(lng: Double, lat: Double) =
        lng in lngs.min() - pad..lngs.max() + pad && lat in lats.min() - pad..lats.max() + pad
    if (!near(location.lng, location.lat)) return "Точка события должна быть на территории или рядом"
    if (area.entrances.size > EventVocabulary.MAX_ENTRANCES) return "Не больше ${EventVocabulary.MAX_ENTRANCES} входов"
    for (e in area.entrances) {
        if (!inRange(e.lng, e.lat) || !near(e.lng, e.lat)) return "Вход должен быть у территории"
        if (e.name.length > 60 || e.name.hasControl()) return "Название входа: до 60 символов"
    }
    return null
}

private fun Event.presentationError(): String? {
    if (presentation !in EventVocabulary.PRESENTATIONS) return "Вид на карте: S, M или L"
    if (presentation == "M" && area == null) return "Для вида M нужна территория"
    if (presentation == "L" && sceneTemplate == null) return "Для вида L нужен шаблон сцены"
    if (sceneTemplate != null) {
        if (presentation != "L") return "Шаблон сцены используется только для вида L"
        if (sceneTemplate !in EventVocabulary.SCENE_TEMPLATES) return "Неизвестный шаблон сцены"
    }
    val params = sceneParams
    if (params != null) {
        if (sceneTemplate == null) return "Параметры сцены без шаблона"
        if (params.length > EventVocabulary.SCENE_PARAMS_LIMIT) return "Параметры сцены слишком большие"
        val parsed = runCatching { Json.parseToJsonElement(params) }.getOrNull()
        if (parsed !is JsonObject) return "Параметры сцены: JSON-объект"
    }
    return null
}

private fun Event.sourceError(): String? {
    if (sourceUrl != null && !validPublicUrl(sourceUrl)) return "Ссылка на источник: адрес http(s) до 500 символов"
    if (sourceName != null && (sourceName.isBlank() || sourceName.length > 120 || sourceName.hasControl()))
        return "Название источника: до 120 символов"
    if (sourceType == "editorial" && sourceUrl == null) return "Для подборки нужна ссылка на источник"
    return null
}

internal fun validPublicUrl(value: String): Boolean {
    if (value.length > 500 || value.hasControl() || value.any { it.isWhitespace() }) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
}

/**
 * What an ordinary account may publish by itself. A level-L scene occupies a whole territory of the
 * city, so it is connected on request; channel labels such as editorial or government are assigned elsewhere.
 */
internal fun Event.accountPolicyError(): String? {
    if (presentation == "L") return "3D-сцена мероприятия подключается по заявке"
    return null
}
