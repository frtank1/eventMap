import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Миграция на AGP 9: composeApp стал KMP-БИБЛИОТЕКОЙ (com.android.kotlin.multiplatform.library).
// Android-приложение вынесено в модуль :androidApp. Здесь — общий UI (commonMain) + входы desktop/iOS/wasm.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    // Android-таргет через новый KMP-плагин (конфиг внутри kotlin{})
    android {
        namespace = "com.eventmap"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.filekit) // кроссплатформенный выбор/сохранение файлов

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
            implementation(libs.voyager.tabNavigator)
            implementation(libs.koin.core)

            implementation(projects.common.core.ui)
            implementation(projects.common.core.network)
            implementation(projects.common.core.platform)
            implementation(projects.common.core.datastore)
            implementation(projects.common.core.files)
            implementation(projects.common.core.biometry)
            implementation(projects.common.core.logger)
            implementation(projects.common.core.connectivity)
            implementation(projects.common.feature.example.ui)
            implementation(projects.common.feature.example.data)
            implementation(projects.common.feature.map.ui)
            implementation(projects.common.feature.auth.ui)
            implementation(projects.common.feature.auth.data)
            implementation(projects.common.feature.auth.domain)
            implementation(projects.common.feature.event.ui)
            implementation(projects.common.feature.event.data)
            implementation(projects.common.feature.event.domain)
            implementation(projects.common.feature.profile.ui)
            implementation(projects.common.feature.profile.data)
            implementation(projects.common.feature.feed.ui)
            implementation(projects.common.feature.feed.data)
            implementation(projects.common.feature.search.ui)
            implementation(projects.common.feature.search.data)
            implementation(projects.common.feature.video.ui)
            implementation(projects.common.feature.video.data)
            implementation(projects.common.feature.business.ui)
            implementation(projects.common.feature.quest.ui)
            implementation(projects.common.feature.payment.ui)
            implementation(projects.common.feature.game.ui)
            implementation(projects.common.feature.invite.ui)
            implementation(projects.common.feature.chat.ui)
            implementation(projects.common.feature.moderation.ui)
            implementation(projects.common.feature.settings.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        getByName("wasmJsMain").dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.eventmap.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.eventmap"
            packageVersion = "1.0.0"
        }
    }
}
