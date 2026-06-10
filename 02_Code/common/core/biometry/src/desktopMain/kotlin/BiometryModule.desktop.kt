package com.eventmap.core.biometry

import org.koin.core.module.Module
import org.koin.dsl.module

actual val biometryModule: Module = module {
    single { BiometricAuthenticator() }
}
