plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}
kotlin { sourceSets { commonMain.dependencies {
    implementation(libs.koin.core)
    implementation(projects.common.core.network)
    implementation(projects.common.feature.video.domain)
} } }
kotlin {
    android {
        namespace = "com.eventmap.feature.video.data"
    }
}
