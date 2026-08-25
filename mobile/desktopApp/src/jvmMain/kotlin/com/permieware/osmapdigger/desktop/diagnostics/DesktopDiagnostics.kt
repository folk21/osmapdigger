package com.permieware.osmapdigger.desktop.diagnostics

import java.awt.GraphicsEnvironment
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/** Filesystem locations owned by Desktop diagnostics. */
internal data class DesktopDiagnosticsPaths(
    val logsDirectory: Path,
    val logFile: Path,
    val runtimeDirectory: Path,
    val sessionMarker: Path,
) {
    companion object {
        fun underHome(home: Path): DesktopDiagnosticsPaths {
            val appRoot = home.resolve(".osmapdigger")
            return DesktopDiagnosticsPaths(
                logsDirectory = appRoot.resolve("logs"),
                logFile = appRoot.resolve("logs/desktop.log"),
                runtimeDirectory = appRoot.resolve("runtime"),
                sessionMarker = appRoot.resolve("runtime/desktop-running"),
            )
        }
    }
}

/** Small file marker that detects a previous process that did not reach normal JVM shutdown. */
internal class DesktopSessionMarker(
    private val path: Path,
) {
    fun begin(content: String): String? {
        val previous =
            if (Files.isRegularFile(path)) {
                runCatching { Files.readString(path) }.getOrNull()
            } else {
                null
            }
        Files.createDirectories(path.parent)
        Files.writeString(
            path,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return previous
    }

    fun complete() {
        Files.deleteIfExists(path)
    }
}

/**
 * Process-local Desktop diagnostics facade.
 *
 * It adds one file handler to the JUL root logger so OsmapDigger and third-party JUL output such as
 * jcefmaven initialization messages share the same persistent diagnostic log without introducing a
 * new logging dependency into shared code.
 */
object DesktopDiagnostics {
    private val started = AtomicBoolean(false)
    private val logger = Logger.getLogger("com.permieware.osmapdigger.desktop")
    private val paths =
        DesktopDiagnosticsPaths.underHome(Paths.get(System.getProperty("user.home")).toAbsolutePath())
    private val marker = DesktopSessionMarker(paths.sessionMarker)
    private var fileHandler: FileHandler? = null
    private var sessionId: String? = null

    /** Initialize persistent logging and the unclean-shutdown marker once per process. */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        val storageReady =
            runCatching {
                Files.createDirectories(paths.logsDirectory)
                Files.createDirectories(paths.runtimeDirectory)
            }.onFailure { failure ->
                System.err.println(
                    "OsmapDigger could not initialize Desktop diagnostic storage: ${failure.message}",
                )
            }.isSuccess
        if (storageReady) attachFileLogging()

        val id = UUID.randomUUID().toString()
        sessionId = id
        val pid = ProcessHandle.current().pid()
        val startedAt = Instant.now()
        val previous =
            if (storageReady) {
                runCatching {
                    marker.begin(
                        "sessionId=$id\npid=$pid\nstartedAt=$startedAt\n",
                    )
                }.onFailure { failure ->
                    warn("startup", "Could not create Desktop session marker", failure)
                }.getOrNull()
            } else {
                null
            }

        if (previous != null) {
            warn(
                "startup",
                "Previous Desktop session did not shut down cleanly; marker=${paths.sessionMarker} previous=${previous.lineSequence().joinToString(" ")}",
            )
            latestFatalErrorReport()?.let { report ->
                warn("startup", "Latest JVM fatal-error report: $report")
            }
        }

        info("startup", "Desktop diagnostics initialized sessionId=$id pid=$pid log=${paths.logFile}")
        logRuntimeFingerprint()

        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    finishFromShutdownHook()
                },
                "osmapdigger-diagnostics-shutdown",
            ),
        )
    }

    fun info(component: String, message: String) {
        logger.log(Level.INFO, "[$component] $message")
    }

    fun warn(component: String, message: String, failure: Throwable? = null) {
        if (failure == null) {
            logger.log(Level.WARNING, "[$component] $message")
        } else {
            logger.log(Level.WARNING, "[$component] $message", failure)
        }
    }

    fun error(component: String, message: String, failure: Throwable? = null) {
        if (failure == null) {
            logger.log(Level.SEVERE, "[$component] $message")
        } else {
            logger.log(Level.SEVERE, "[$component] $message", failure)
        }
    }

    /** Log START/OK/FAILED around one synchronous Desktop lifecycle phase. */
    fun <T> measure(component: String, block: () -> T): T {
        val startedAt = System.nanoTime()
        info(component, "START")
        return try {
            block().also {
                info(component, "OK durationMs=${elapsedMillis(startedAt)}")
            }
        } catch (failure: Throwable) {
            error(component, "FAILED durationMs=${elapsedMillis(startedAt)} message=${failure.message}", failure)
            throw failure
        }
    }

    private fun attachFileLogging() {
        runCatching {
            FileHandler(paths.logFile.toString(), true).also { handler ->
                handler.level = Level.ALL
                handler.formatter = DesktopLogFormatter()
                Logger.getLogger("").addHandler(handler)
                fileHandler = handler
            }
        }.onFailure { failure ->
            System.err.println("OsmapDigger could not initialize persistent Desktop logging: ${failure.message}")
        }
    }

    private fun logRuntimeFingerprint() {
        info(
            "runtime",
            listOf(
                "os=${System.getProperty("os.name")}",
                "osVersion=${System.getProperty("os.version")}",
                "arch=${System.getProperty("os.arch")}",
                "java=${System.getProperty("java.version")}",
                "vendor=${System.getProperty("java.vendor")}",
                "javaHome=${System.getProperty("java.home")}",
                "userDir=${System.getProperty("user.dir")}",
                "headless=${GraphicsEnvironment.isHeadless()}",
            ).joinToString(" "),
        )
        info(
            "runtime",
            "jvmArgs=${ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" ")}",
        )
    }

    private fun latestFatalErrorReport(): Path? =
        runCatching {
            Files.list(paths.logsDirectory).use { entries ->
                entries
                    .filter { path ->
                        val name = path.fileName.toString()
                        name.startsWith("hs_err_pid") && name.endsWith(".log")
                    }
                    .max(Comparator.comparingLong { path -> Files.getLastModifiedTime(path).toMillis() })
                    .orElse(null)
            }
        }.getOrNull()

    private fun finishFromShutdownHook() {
        runCatching { marker.complete() }
            .onFailure { failure -> warn("shutdown", "Could not remove session marker", failure) }
        info("shutdown", "Desktop JVM shutdown sessionId=${sessionId ?: "unknown"}")
        fileHandler?.let { handler ->
            runCatching { handler.flush() }
            runCatching { Logger.getLogger("").removeHandler(handler) }
            runCatching { handler.close() }
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000L
}

private class DesktopLogFormatter : Formatter() {
    private val dateTimeFormatter =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    override fun format(record: LogRecord): String {
        val rendered =
            buildString {
                append(dateTimeFormatter.format(Instant.ofEpochMilli(record.millis)))
                append(' ')
                append(record.level.name)
                append(" [")
                append(record.loggerName ?: "unknown")
                append("] ")
                append(formatMessage(record))
                append(System.lineSeparator())
                record.thrown?.let { failure ->
                    val buffer = StringWriter()
                    failure.printStackTrace(PrintWriter(buffer))
                    append(buffer.toString())
                }
            }
        return rendered
    }
}
