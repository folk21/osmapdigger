import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Returns the MapLibre JNI capability published for the current Desktop host, or null when
 * MapLibre Compose does not publish a compatible native runtime for this OS/architecture.
 *
 * macOS Intel is intentionally unsupported by MapLibre Compose 0.13.x. Keeping this decision
 * in Gradle prevents an unavailable native artifact from making the whole Desktop application
 * un-runnable; the Desktop source set receives a fallback map implementation instead.
 */
fun detectDesktopMapLibreCapability(): String? {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    return when {
        os == "mac os x" && arch in setOf("arm64", "aarch64") -> "macos-aarch64-metal"
        os.startsWith("linux") && arch in setOf("x86_64", "amd64") -> "linux-amd64-opengl"
        os.startsWith("windows") && arch in setOf("x86_64", "amd64") -> "windows-amd64-opengl"
        else -> null
    }
}

val desktopMapLibreCapability = detectDesktopMapLibreCapability()

kotlin {
    android {
        namespace = "com.permieware.osmapdigger.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        // Use explicit source-set lookup instead of generated Kotlin DSL accessors.
        // This is more robust when source-set configuration is conditional because accessors
        // such as `desktopMain` are not always available as script properties at compile time.
        val commonMain = getByName("commonMain")
        val androidMain = getByName("androidMain")
        val desktopMain = getByName("desktopMain")
        val commonTest = getByName("commonTest")

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.maplibre.compose)
        }

        if (desktopMapLibreCapability != null) {
            desktopMain.kotlin.srcDir("src/desktopMapLibreMain/kotlin")
            desktopMain.dependencies {
                implementation(libs.maplibre.compose)
            }
        } else {
            desktopMain.kotlin.srcDir("src/desktopFallbackMain/kotlin")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
