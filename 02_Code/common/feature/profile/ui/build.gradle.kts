plugins { id("core.kmp.compose") }
kotlin { sourceSets {
    commonMain.dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        implementation(libs.voyager.screenModel)
        implementation(libs.voyager.navigator)
        implementation(libs.voyager.koin)
        implementation(libs.koin.core)
        implementation(projects.common.core.ui)
            implementation(projects.common.core.datastore) // PulseSettings (тумблер I2)
        implementation(projects.common.core.network)
        implementation(projects.common.feature.profile.domain)
    }
    androidMain.dependencies {
        implementation(libs.compose.uiToolingPreview)
        implementation(libs.androidx.activity.compose)
    }
} }
kotlin {
    android {
        namespace = "com.eventmap.feature.profile.ui"
    }
}
