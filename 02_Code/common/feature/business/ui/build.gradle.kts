// Активен (include в settings.gradle.kts, зависимость composeApp).
// Подключён к бэку /business/* и POST /quests (07.07.2026).
plugins {
    id("core.kmp.compose")
    alias(libs.plugins.serialization) // DTO бизнеса/квестов (data/BusinessData.kt)
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
            implementation(libs.compottie)
            implementation(libs.filekit) // фото документов для заявки (M5)
            implementation(libs.kotlinx.datetime) // сроки квеста (endsAt = сегодня + N дней)
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform) // GeoProvider: «моя позиция» в точках квеста
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.business.ui"
    }
}
