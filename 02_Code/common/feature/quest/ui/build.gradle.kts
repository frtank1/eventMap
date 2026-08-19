// Активен (include в settings.gradle.kts). Подключён к бэку /quests/* (01.07.2026).
plugins {
    id("core.kmp.compose")
    alias(libs.plugins.serialization) // DTO квестов (data/QuestData.kt)
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
            implementation(libs.filekit) // фото-чекин Q3: выбор снимка из галереи
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform) // GeoProvider для гео-чекина

        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.quest.ui"
    }
}
