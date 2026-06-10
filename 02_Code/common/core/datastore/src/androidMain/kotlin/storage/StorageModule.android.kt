package com.eventmap.core.datastore

import PlatformConfiguration
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeMemoryPolicy
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// KSafe -> SecureStorage. Если имена методов KSafe (get/put) отличаются — править здесь.
private fun KSafe.asSecureStorage(): SecureStorage = object : SecureStorage {
    override fun getString(key: String): String? = getDirect(key, "").ifEmpty { null }
    override fun putString(key: String, value: String) { putDirect(key, value) }
    override fun remove(key: String) { putDirect(key, "") }
}

actual val storageModule: Module
    get() = module {
        single<SecureStorage>(named("prefs")) {
            val config = get<PlatformConfiguration>()
            KSafe(
                context = config.androidContext,
                fileName = "prefs",
                memoryPolicy = KSafeMemoryPolicy.PLAIN_TEXT
            ).asSecureStorage()
        }

        single<SecureStorage>(named("vault")) {
            val config = get<PlatformConfiguration>()
            KSafe(
                context = config.androidContext,
                fileName = "vault"
            ).asSecureStorage()
        }
    }
