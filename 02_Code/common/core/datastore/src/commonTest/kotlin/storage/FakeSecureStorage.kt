package com.eventmap.core.datastore

class FakeSecureStorage : SecureStorage {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}
