plugins {
    id("core.kmp.compose")
    alias(libs.plugins.serialization) // черновик формы D6 (CreateForm → JSON)
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
            implementation(libs.kotlinx.datetime) // дата создания события (DatePicker → ISO)
            implementation(libs.kotlinx.serialization.json) // черновик формы D6
            implementation(libs.filekit) // выбор обложки/ролика из галереи (kind image/video)
            implementation(projects.common.core.datastore) // EventDraftStorage (черновик D6)
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform) // ShareService (рилс D8), напоминания
            implementation(projects.common.feature.event.domain)
            implementation(projects.common.feature.map.ui) // EventMap для пикера локации
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.event.ui"
    }
}
