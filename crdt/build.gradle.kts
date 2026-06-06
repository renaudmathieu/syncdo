import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.kotlinxBinaryCompatibilityValidator)
    alias(libs.plugins.vanniktechMavenPublish)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.doppio.syncdo.crdt"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))

    pom {
        name.set("SyncDO CRDT")
        description.set(
            "Generic CRDT primitives for Kotlin Multiplatform: VectorClock, LwwRegister, " +
                "Observed-Remove Set, and composable Delta/DeltaState abstractions."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/doppio/syncdo")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("doppio")
                name.set("Doppio")
            }
        }
        scm {
            url.set("https://github.com/doppio/syncdo")
            connection.set("scm:git:git://github.com/doppio/syncdo.git")
            developerConnection.set("scm:git:ssh://git@github.com/doppio/syncdo.git")
        }
    }
}
