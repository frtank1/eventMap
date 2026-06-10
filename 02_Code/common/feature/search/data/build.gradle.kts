plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}
kotlin { sourceSets { commonMain.dependencies {
    implementation(libs.koin.core)
    implementation(projects.common.core.network)
    implementation(projects.common.feature.search.domain)
} } }
android { namespace = "com.eventmap.feature.search.data" }
