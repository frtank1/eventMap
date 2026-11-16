plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.common.core.network)
            implementation(projects.common.feature.event.domain)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlin.testJunit)
        }
    }
}
kotlin {
    android {
        namespace = "com.eventmap.feature.event.data"
    }
}
