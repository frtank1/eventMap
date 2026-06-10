plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}

android {
    namespace = "com.eventmap.core.platform"
}
