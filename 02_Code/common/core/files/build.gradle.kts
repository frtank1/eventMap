plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.common.core.datastore)
            implementation(projects.common.core.platform)
            implementation(projects.common.core.network)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.eventmap.core.files"
}
