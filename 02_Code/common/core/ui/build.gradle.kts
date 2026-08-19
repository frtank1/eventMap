plugins {
    id("core.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // api: иконки доступны всем фичам через core.ui
            api(libs.compose.materialIconsExtended)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.components.resources)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.transitions)
            implementation(projects.common.core.network)
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            // VideoPlayer (views/video): ExoPlayer + PlayerView
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            // CameraRecorder (views/camera): CameraX превью + запись
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.video)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.lifecycle.runtimeCompose) // LocalLifecycleOwner
            // QrScanner (views/qr): распознавание QR через ImageAnalysis
            implementation(libs.zxing.core)
        }
    }
}

kotlin {
    android {
        namespace = "com.eventmap.core.ui"
    }
}

// Фиксируем пакет генерируемого Res-класса, чтобы он не зависел от rootProject.name.
compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "com.eventmap.core.ui.resources"
}
