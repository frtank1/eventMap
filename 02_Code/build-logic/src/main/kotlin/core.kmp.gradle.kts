import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Общая конфигурация KMP-библиотечного модуля: таргеты + android-блок.
// Каждый модуль задаёт только свой android.namespace и свои зависимости.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

// единый источник версий — каталог libs (gradle/libs.versions.toml)
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
}

android {
    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
    }
}
