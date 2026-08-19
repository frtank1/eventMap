// Активен (include в settings.gradle.kts). Подключён к бэку /game/* (01.07.2026).
plugins {
    id("core.kmp.compose")
    alias(libs.plugins.serialization) // DTO game hub (data/GameData.kt)
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.koin.core)
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform) // GeoProvider: капсулы «здесь и сейчас»
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.game.ui"
    }
}
