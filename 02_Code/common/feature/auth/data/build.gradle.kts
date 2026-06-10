plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(projects.common.core.network)
            implementation(projects.common.core.datastore)
            implementation(projects.common.feature.auth.domain)
        }
    }
}

android {
    namespace = "com.eventmap.feature.auth.data"
}
