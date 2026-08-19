plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.common.core.platform)
        }

        androidMain.dependencies {
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.core.ktx)
        }
    }
}

kotlin {
    android {
        namespace = "com.eventmap.core.biometry"
    }
}
