plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinxSerialization)
    application
}

kotlin {
    compilerOptions {
        // Kotlin 2.3.x is a pre-release; skip the check so the JVM-only server module
        // can load classes produced by the KMP-compiled shared module.
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

group = "com.doppio.syncdo"
version = "1.0.0"
application {
    mainClass.set("com.doppio.syncdo.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(projects.sync)
    implementation(libs.logback)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}