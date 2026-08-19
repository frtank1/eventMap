// ДРАФТ (создан при аудите 13.06.2026). Модуль НЕ активен, пока не добавишь
// include(":common:feature:payment:ui") в settings.gradle.kts и зависимость в composeApp.
// Перед первой сборкой сверь набор зависимостей с реальными импортами экранов.
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

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.koin.core)
            implementation(libs.kotlinx.datetime)
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.payment.ui"
    }
}
