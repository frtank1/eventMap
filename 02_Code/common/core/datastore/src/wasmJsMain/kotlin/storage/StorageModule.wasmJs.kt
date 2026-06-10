package com.eventmap.core.datastore

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// web: шифрования нет, храним в localStorage через multiplatform-settings
private fun Settings.asSecureStorage(): SecureStorage = object : SecureStorage {
    override fun getString(key: String): String? = this@asSecureStorage.getStringOrNull(key)
    override fun putString(key: String, value: String) { this@asSecureStorage.putString(key, value) }
    override fun remove(key: String) { this@asSecureStorage.remove(key) }
}

actual val storageModule: Module
    get() = module {
        single<SecureStorage>(named("prefs")) { Settings().asSecureStorage() }
        single<SecureStorage>(named("vault")) { Settings().asSecureStorage() }
    }
