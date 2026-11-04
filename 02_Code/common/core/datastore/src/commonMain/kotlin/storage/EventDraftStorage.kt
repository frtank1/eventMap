package com.eventmap.core.datastore

// Черновик формы «создать событие» (D6): JSON формы переживает выход с экрана.
// Чистится при успешной публикации. Медиа в черновик не входят (байты не храним).
class EventDraftStorage(
    private val storage: SecureStorage,
    private val activeOwner: () -> String?,
) {

    fun ownerId(): String? = activeOwner()

    fun save(json: String, ownerId: String? = activeOwner()) {
        key(ownerId)?.let { storage.putString(it, json) }
    }

    fun load(ownerId: String? = activeOwner()): String? =
        key(ownerId)?.let(storage::getString)?.takeIf { it.isNotBlank() }

    fun clear(ownerId: String? = activeOwner()) {
        key(ownerId)?.let { storage.putString(it, "") }
    }

    // Never import the legacy unowned v1 draft into an arbitrary account.
    private fun key(ownerId: String?): String? = ownerId
        ?.takeIf { it.isNotBlank() && it == activeOwner() }
        ?.let { "create_event_draft_v2:$it" }
}
