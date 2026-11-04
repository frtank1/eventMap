plugins {
    id("core.kmp.compose")
    alias(libs.plugins.serialization) // DTO пульса карты (data/MapPulseData.kt)
}

// Opt in after provisioning dgissdk.key; existing installations retain their map.
val mapProvider = providers.gradleProperty("eventmap.mapProvider").orElse("mapbox").get()
require(mapProvider in setOf("mapbox", "2gis")) {
    "eventmap.mapProvider must be mapbox or 2gis"
}

kotlin {
    sourceSets {
        androidMain {
            kotlin.srcDir(if (mapProvider == "2gis") "src/android2gis/kotlin" else "src/androidMapbox/kotlin")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.voyager.screenModel) // ScreenModel — супертип BaseScreenModel
            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform) // GeoProvider (гео-пинг I2)
            implementation(projects.common.core.datastore) // PulseSettings (opt-in)
            implementation(projects.common.feature.event.domain)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            if (mapProvider == "2gis") {
                implementation("ru.dgis.sdk:sdk-map:14.2.0")
            } else {
                implementation("com.mapbox.maps:android:11.8.0")
            }
        }
    }

    // iOS is still a placeholder; Android provider selection does not implement iOS.
}

kotlin {
    android {
        namespace = "com.eventmap.feature.map.ui"
    }
}
