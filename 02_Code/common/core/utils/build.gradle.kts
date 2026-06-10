plugins {
    id("core.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.multiplatform.settings.core)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.eventmap.core.utils"
}
