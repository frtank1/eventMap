plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.coroutines.core)
            // Ktor core
            api(libs.ktor.client.core)

            // Ktor JSON
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Logging
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            implementation(libs.koin.core)
            implementation(libs.voyager.screenModel)
            implementation(projects.common.core.datastore)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
        }

        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        getByName("wasmJsMain").dependencies {
            implementation(libs.ktor.client.js)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

kotlin {
    android {
        namespace = "com.eventmap.core.network"
    }
}
