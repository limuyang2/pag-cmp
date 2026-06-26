import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLint)
    `maven-publish`
    signing
}

val libraryGroup = "io.github.limuyang2"
val libraryVersion = "0.1.1"

group = libraryGroup
version = libraryVersion

kotlin {

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    android {
        namespace = "io.github.limuyang2.libpag.cmp"
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }
        minSdk = 24

    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "lib-pag-cmpKit"

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    swiftPMDependencies {
        discoverClangModulesImplicitly = false
        swiftPackage(
            url = url("https://github.com/libpag/pag-ios.git"),
            version = exact("4.5.70"),
            products = listOf(product("libpag")),
        )
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                // Add KMP dependencies here
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.skiko)
            }
        }

        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.wrappers.browser)
            }
        }

        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.wrappers.browser)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.libpag)
                implementation(libs.compose.ui)
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }


        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }

}

//---------- maven upload info -----------------------------------

var signingKeyId = ""
var signingPassword = ""
var secretKeyRingFile = ""

val localProperties = project.rootProject.file("local.properties")
if (localProperties.exists()) {
    val properties = Properties()
    InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
        properties.load(reader)
    }
    signingKeyId = properties.getProperty("signing.keyId", "")
    signingPassword = properties.getProperty("signing.password", "")
    secretKeyRingFile = properties.getProperty("signing.secretKeyRingFile", "")
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    description = "Assembles an empty javadoc jar required by Maven Central validation."
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = libraryGroup
        version = libraryVersion

        artifactId = when (name) {
            "kotlinMultiplatform" -> "lib-pag-cmp"
            "android" -> "lib-pag-cmp-android"
            "jvm" -> "lib-pag-cmp-jvm".also { artifact(emptyJavadocJar) }
            "js" -> "lib-pag-cmp-js"
            "wasmJs" -> "lib-pag-cmp-wasm-js"
            "iosArm64" -> "lib-pag-cmp-ios-arm64"
            "iosSimulatorArm64" -> "lib-pag-cmp-ios-simulator-arm64"
            else -> artifactId
        }

        pom {
            name.set("lib-pag-cmp")
            description.set("A Compose Multiplatform PAG animation library.")
            url.set("https://github.com/limuyang2/lib-pag-cmp")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://github.com/limuyang2/lib-pag-cmp/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set("limuyang2")
                    name.set("limuyang")
                    email.set("limuyang2@hotmail.com")
                }
            }

            scm {
                connection.set("scm:git@github.com:limuyang2/lib-pag-cmp.git")
                developerConnection.set("scm:git@github.com:limuyang2/lib-pag-cmp.git")
                url.set("https://github.com/limuyang2/lib-pag-cmp")
            }
        }
    }

    repositories {
        maven {
            name = "Maven"
            setUrl(rootProject.layout.projectDirectory.dir("RepoDir"))
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it is Sign }) {
        allprojects {
            extra["signing.keyId"] = signingKeyId
            extra["signing.secretKeyRingFile"] = secretKeyRingFile
            extra["signing.password"] = signingPassword
        }
    }
}

signing {
    sign(publishing.publications)
}
