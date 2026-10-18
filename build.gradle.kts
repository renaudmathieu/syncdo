plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.vanniktechMavenPublish) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlinxBinaryCompatibilityValidator) apply false
}
// Aggregated API reference of the published libraries: ./gradlew dokkaGenerate
// Output: build/dokka/html/index.html
dokka {
    moduleName.set("SyncDO")
}

dependencies {
    dokka(project(":crdt"))
    dokka(project(":sync"))
}
