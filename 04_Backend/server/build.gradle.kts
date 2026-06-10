plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.eventmap"
version = "0.1.0"

repositories { mavenCentral() }

val ktor = "2.3.12"
val exposed = "0.53.0"
val koin = "3.5.6"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-cors:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    // DI
    implementation("io.insert-koin:koin-ktor:$koin")
    implementation("io.insert-koin:koin-logger-slf4j:$koin")
    // DB
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    // auth hashing
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

application { mainClass.set("com.eventmap.server.ApplicationKt") }
kotlin { jvmToolchain(17) }
