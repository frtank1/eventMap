package com.eventmap.core.datastore

// Приватность «Пульса города» (I2, API §8.5): гео-пинг /map/ping шлётся ТОЛЬКО
// при включённом тумблере (Настройки → Sharing). По умолчанию ВЫКЛ (opt-in).
class PulseSettings(private val storage: SecureStorage) {

    fun isSharingEnabled(): Boolean = storage.getString(KEY_SHARING) == "1"

    fun setSharingEnabled(enabled: Boolean) {
        storage.putString(KEY_SHARING, if (enabled) "1" else "0")
    }

    private companion object {
        const val KEY_SHARING = "pulse_sharing_enabled"
    }
}
