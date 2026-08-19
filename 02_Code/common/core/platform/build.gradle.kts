plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core) // GeoProvider (suspend one-shot)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime) // LocalReminders: напоминания «за час»
            implementation(libs.androidx.core.ktx) // NotificationCompat/NotificationManagerCompat
        }
    }
}

kotlin {
    android {
        namespace = "com.eventmap.core.platform"
    }
}
