plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}
kotlin { sourceSets { commonMain.dependencies {
    implementation(projects.common.core.network)
    implementation(libs.koin.core)
} } }
android { namespace = "com.eventmap.feature.feed.domain" }
