plugins {
    id("core.kmp")
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Storage abstraction + DI. KSafe тут НЕТ — он только в платформах,
            // чтобы commonMain (и будущий wasm) от него не зависел.
            implementation(libs.multiplatform.settings.core)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.common.core.platform)
        }

        androidMain.dependencies {
            implementation(libs.ksafe.core)
        }

        iosMain.dependencies {
            implementation(libs.ksafe.core)
        }

        getByName("desktopMain").dependencies {
            implementation(libs.ksafe.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.eventmap.core.datastore"
}
