import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android-приложение, вынесенное из composeApp при миграции на AGP 9
// (com.android.application + KMP больше не поддерживается — приложение в отдельном модуле).
// Содержит только Android-вход: MainActivity, EventMapApp, манифест, ресурсы. Общий UI — в :composeApp.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.eventmap"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.eventmap"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(projects.composeApp)            // общий UI и PlatformSDK
    // модули, что нужны Android-входу напрямую (composeApp тянет их через implementation,
    // поэтому транзитивно не видны): PlatformConfiguration, BiometricActivityHolder, FileKit
    implementation(projects.common.core.platform)
    implementation(projects.common.core.biometry)
    implementation(libs.filekit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
