import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

fun mapLibreDesktopCapability(): String? {
    val os = System.getProperty("os.name").lowercase()
    val arch =
        when (System.getProperty("os.arch").lowercase()) {
            "x86_64", "amd64" -> "amd64"
            "aarch64", "arm64" -> "aarch64"
            else -> return null
        }

    return when {
        os == "mac os x" && arch == "aarch64" -> "macos-aarch64-metal"
        os.startsWith("linux") && arch == "amd64" -> "linux-amd64-opengl"
        os.startsWith("windows") && arch == "amd64" -> "windows-amd64-opengl"
        else -> null
    }
}


fun isIntelMacHost(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return os == "mac os x" && arch in setOf("x86_64", "amd64")
}

val mapLibreCapability = mapLibreDesktopCapability()
val intelMacHost = isIntelMacHost()
val mapLibreNativeBindings =
    "org.maplibre.compose:maplibre-native-bindings-jni:${libs.versions.maplibreCompose.get()}"


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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jcef)
            implementation(libs.maplibre.gl.webjar)
            implementation(libs.pmtiles.reader)

            if (intelMacHost) {
                runtimeOnly(libs.jcef.natives.macosx.amd64)
            }

            if (mapLibreCapability != null) {
                runtimeOnly(mapLibreNativeBindings) {
                    capabilities {
                        requireCapability(
                            "org.maplibre.compose:maplibre-native-bindings-jni-$mapLibreCapability",
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
        if (intelMacHost) {
            jvmArgs += listOf(
                "--add-modules=jdk.httpserver",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }
    }
}
