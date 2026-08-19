import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Общая конфигурация KMP-библиотечного модуля: таргеты + android-блок.
// Миграция на AGP 9: вместо com.android.library — новый KMP-плагин
// com.android.kotlin.multiplatform.library; android-конфиг переехал внутрь kotlin { android { } }.
// Каждый модуль задаёт только свой namespace через kotlin { android { namespace = "..." } }.
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// единый источник версий — каталог libs (gradle/libs.versions.toml)
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        // ресурсы в новом плагине по умолчанию выключены — включаем (некоторые модули их используют)
        androidResources {
            enable = true
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
}
