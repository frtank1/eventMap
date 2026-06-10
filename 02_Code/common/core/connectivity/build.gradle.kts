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
        getByName("wasmJsMain").dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

android {
    namespace = "com.eventmap.core.connectivity"
}
