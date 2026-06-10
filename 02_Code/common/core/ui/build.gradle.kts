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
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.components.resources)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(projects.common.core.network)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.eventmap.core.ui"
}

// Фиксируем пакет генерируемого Res-класса, чтобы он не зависел от rootProject.name.
compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "com.eventmap.core.ui.resources"
}
