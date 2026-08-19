plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}

kotlin {
    android {
        namespace = "com.eventmap.core.logger"
    }
}
