package com.eventmap.core.datastore

// Платформенно-независимый контракт хранилища строк.
// android/ios/desktop -> KSafe (шифрование), wasm -> multiplatform-settings.
interface SecureStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
