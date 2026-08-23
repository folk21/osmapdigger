import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Returns the platform-specific MapLibre JNI capability available for this Desktop host.
 * A null result means the current host is intentionally running the non-MapLibre fallback.
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
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.shared)
            implementation(compose.desktop.currentOs)
            implementation(libs.sqlite.jdbc)
            implementation(libs.kotlinx.serialization.json)

            // Only request a native runtime capability that MapLibre Compose actually publishes.
            // Intel macOS has no MapLibre Compose JNI runtime in 0.13.x, so it deliberately skips
            // this dependency and uses the shared module's Desktop fallback map implementation.
            desktopMapLibreCapability?.let { capability ->
                runtimeOnly(
                    "org.maplibre.compose:maplibre-native-bindings-jni:${libs.versions.maplibreCompose.get()}",
                ) {
                    capabilities {
                        requireCapability(
                            "org.maplibre.compose:maplibre-native-bindings-jni-$capability",
                        )
                    }
                }
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.permieware.osmapdigger.desktop.MainKt"
    }
}
