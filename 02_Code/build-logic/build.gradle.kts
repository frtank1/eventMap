plugins {
    `kotlin-dsl`
}

dependencies {
    // плагины нужны на classpath, чтобы convention-плагин мог их применять
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.android)
    implementation(libs.plugin.compose)
    implementation(libs.plugin.compose.compiler)
}
