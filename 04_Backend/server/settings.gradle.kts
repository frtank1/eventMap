// Авто-загрузка JDK 17 для toolchain, если на машине его нет (CI, чистая среда)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "eventmap-server"
