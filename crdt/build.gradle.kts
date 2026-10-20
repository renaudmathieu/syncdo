import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost
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
    // Published library: every public declaration must say so, and must have an explicit type.
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm {
        // Pin the bytecode level of the published -jvm artifact instead of inheriting it
        // from whichever JDK runs Gradle. Matches the Android target.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

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

    // Maven Central through the Central Portal. Credentials and the signing key come from
    // ORG_GRADLE_PROJECT_mavenCentralUsername / mavenCentralPassword / signingInMemoryKey /
    // signingInMemoryKeyPassword (see .github/workflows/publish.yml). Signing is skipped for
    // SNAPSHOT versions and for publishToMavenLocal when no key is configured.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    pom {
        name.set("SyncDO CRDT")
        description.set(
            "Generic CRDT primitives for Kotlin Multiplatform: CrdtState, VectorClock, " +
                "LwwRegister and Observed-Remove Set, composable into your own CRDTs."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/renaudmathieu/syncdo")
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
            url.set("https://github.com/renaudmathieu/syncdo")
            connection.set("scm:git:git://github.com/renaudmathieu/syncdo.git")
            developerConnection.set("scm:git:ssh://git@github.com/renaudmathieu/syncdo.git")
        }
    }
}
