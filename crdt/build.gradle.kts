import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.kotlinxBinaryCompatibilityValidator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktechMavenPublish)
}

kotlin {
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
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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

dokka {
    moduleName.set("syncdo-crdt")
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        reportUndocumented.set(true)
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl("https://github.com/renaudmathieu/syncdo/tree/main/crdt/src")
            remoteLineSuffix.set("#L")
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))

    pom {
        name.set("SyncDO CRDT")
        description.set(
            "Generic CRDT primitives for Kotlin Multiplatform: CrdtState, VectorClock, " +
                "LwwRegister and Observed-Remove Set, composable into your own CRDTs."
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
