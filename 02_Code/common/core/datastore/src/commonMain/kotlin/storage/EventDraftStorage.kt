package com.eventmap.core.datastore

// Черновик формы «создать событие» (D6): JSON формы переживает выход с экрана.
// Чистится при успешной публикации. Медиа в черновик не входят (байты не храним).
class EventDraftStorage(private val storage: SecureStorage) {

    fun save(json: String) = storage.putString(KEY_DRAFT, json)

    fun load(): String? = storage.getString(KEY_DRAFT)?.takeIf { it.isNotBlank() }

    fun clear() = storage.putString(KEY_DRAFT, "")

    private companion object {
        const val KEY_DRAFT = "create_event_draft_v1"
    }
}
