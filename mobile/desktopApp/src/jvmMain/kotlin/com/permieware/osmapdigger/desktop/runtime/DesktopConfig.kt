package com.permieware.osmapdigger.desktop.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Desktop startup configuration.
 *
 * Configuration lookup is independent from the Gradle working directory because desktopApp
 * is a Gradle subproject and may be started from different locations.
 */
@Serializable
data class DesktopConfig(
    val defaultDatasetPackage: String,
)

object DesktopConfigLoader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Loads configuration from supported locations.
     *
     * Priority:
     * 1. User configuration.
     * 2. Project configuration for development.
     * 3. Classpath resource.
     */
    fun load(): DesktopConfig? {
        val candidates = listOfNotNull(
            userConfig(),
            projectConfig(),
        )

        val configFile = candidates.firstOrNull { Files.exists(it) }

        println("Desktop configuration candidates: $candidates")
        println("Selected desktop configuration: $configFile")

        return when {
            configFile != null -> parse(configFile)
            else -> loadClasspathConfig()
        }
    }

    fun resolvePackage(config: DesktopConfig): Path {
        val configured = Paths.get(config.defaultDatasetPackage)

        if (configured.isAbsolute) {
            return configured
        }

        val configFile = listOfNotNull(userConfig(), projectConfig())
            .firstOrNull { Files.exists(it) }

        // Project configuration lives in <project-root>/config.
        // Relative dataset paths are resolved from the project root, not from the config directory.
        return configFile
            ?.parent
            ?.parent
            ?.resolve(configured)
            ?.normalize()
            ?: configured
    }

    private fun parse(path: Path): DesktopConfig? =
        runCatching {
            json.decodeFromString<DesktopConfig>(Files.readString(path))
        }.onFailure {
            println("Cannot parse desktop config $path: ${it.message}")
        }.getOrNull()

    private fun userConfig(): Path =
        Paths.get(
            System.getProperty("user.home"),
            ".osmapdigger",
            "desktop-config.json",
        )

    private fun projectConfig(): Path? {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

        while (current.parent != null) {
            val candidate = current.resolve("config/desktop-config.json")
            if (Files.exists(candidate)) {
                return candidate
            }
            current = current.parent
        }

        return null
    }

    private fun loadClasspathConfig(): DesktopConfig? =
        object {}.javaClass.classLoader
            .getResourceAsStream("desktop-config.json")
            ?.bufferedReader()
            ?.use { reader ->
                runCatching {
                    json.decodeFromString<DesktopConfig>(reader.readText())
                }.getOrNull()
            }
}
