plugins {
    id("core.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.voyager.screenModel) // ScreenModel — супертип BaseScreenModel
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.feature.event.domain)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            // Mapbox Android SDK — токен скачивания в ~/.gradle/gradle.properties (см. MAPBOX_SETUP.md)
            implementation("com.mapbox.maps:android:11.8.0")
        }
    }

    // Mapbox iOS SDK подключается через cocoapods — см. MAPBOX_SETUP.md
}

android {
    namespace = "com.eventmap.feature.map.ui"
}
