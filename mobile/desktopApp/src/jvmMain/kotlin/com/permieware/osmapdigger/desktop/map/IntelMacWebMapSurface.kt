package com.permieware.osmapdigger.desktop.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.Settlement
import com.permieware.osmapdigger.runtime.MapPackage
import com.permieware.osmapdigger.ui.PlatformMapSurface
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JPanel

/**
 * Intel macOS map renderer backed by JCEF + MapLibre GL JS.
 *
 * MapLibre Compose does not publish an Intel macOS JNI runtime. This renderer keeps the browser
 * and local tile-serving concerns inside the Desktop host while reusing the same package style,
 * PMTiles artifact, result GeoJSON, and selected-settlement semantics as the native renderers.
 */
class IntelMacWebMapSurface private constructor() : PlatformMapSurface, Closeable {
    private val sessions = mutableSetOf<WebMapSession>()
    private val cefRuntime: Result<CefRuntime> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            DesktopDiagnostics.measure("jcef.init") { CefRuntime.create() }
        }.onFailure { failure ->
            DesktopDiagnostics.error("jcef.init", "JCEF initialization failed", failure)
        }
    }

    @Composable
    override fun Render(
        modifier: Modifier,
        datasetInfo: DatasetInfo?,
        mapPackage: MapPackage,
        results: List<Settlement>,
        selected: Settlement?,
    ) {
        if (datasetInfo == null || mapPackage.styleJson == null || mapPackage.localMapUri == null) {
            MapUnavailable(
                modifier = modifier,
                message = if (datasetInfo == null) "Loading dataset…" else "This package has no generated map.",
            )
            return
        }

        val runtimeResult = remember { cefRuntime }
        val runtime = runtimeResult.getOrNull()
        if (runtime == null) {
            MapUnavailable(
                modifier = modifier,
                message = "Intel macOS web map could not start: ${runtimeResult.exceptionOrNull()?.message ?: "unknown JCEF error"}",
            )
            return
        }

        val sessionResult =
            remember(mapPackage.localMapUri, mapPackage.styleJson, datasetInfo.id) {
                runCatching {
                    runtime.openSession(
                        datasetInfo = datasetInfo,
                        mapPackage = mapPackage,
                    ).also { session ->
                        synchronized(sessions) { sessions += session }
                    }
                }.onFailure { failure ->
                    DesktopDiagnostics.error(
                        "map.session",
                        "Could not open Intel macOS map session dataset=${datasetInfo.id}",
                        failure,
                    )
                }
            }
        val session = sessionResult.getOrNull()
        if (session == null) {
            MapUnavailable(
                modifier = modifier,
                message = "Intel macOS web map could not open this dataset: ${sessionResult.exceptionOrNull()?.message ?: "unknown error"}",
            )
            return
        }

        DisposableEffect(session) {
            onDispose {
                synchronized(sessions) { sessions -= session }
                session.close()
            }
        }

        SwingPanel(
            factory = { session.component },
            modifier = modifier,
            update = {
                session.update(results = results, selected = selected)
            },
        )
    }

    override fun close() {
        val active = synchronized(sessions) { sessions.toList().also { sessions.clear() } }
        DesktopDiagnostics.info("map.renderer", "Closing Intel macOS renderer sessions=${active.size}")
        active.forEach { session ->
            runCatching { session.close() }
                .onFailure { failure -> DesktopDiagnostics.warn("map.session", "Session close failed", failure) }
        }
        cefRuntime.getOrNull()?.close()
    }

    companion object {
        /** Return the Intel-only renderer without changing behavior on other Desktop hosts. */
        fun createIfSupported(
            osName: String = System.getProperty("os.name"),
            osArch: String = System.getProperty("os.arch"),
        ): IntelMacWebMapSurface? =
            if (isSupportedHost(osName, osArch)) IntelMacWebMapSurface() else null

        internal fun isSupportedHost(
            osName: String,
            osArch: String,
        ): Boolean {
            val os = osName.lowercase()
            val arch = osArch.lowercase()
            return os == "mac os x" && (arch == "x86_64" || arch == "amd64")
        }
    }
}

@Composable
private fun MapUnavailable(
    modifier: Modifier,
    message: String,
) {
    Box(modifier.padding(24.dp)) {
        BasicText(
            text = message,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private class CefRuntime private constructor(
    private val app: CefApp,
) : Closeable {
    fun openSession(
        datasetInfo: DatasetInfo,
        mapPackage: MapPackage,
    ): WebMapSession {
        val localMapUri = requireNotNull(mapPackage.localMapUri)
        val mapPath = Paths.get(java.net.URI(localMapUri))
        require(Files.isRegularFile(mapPath)) { "PMTiles file not found: $mapPath" }

        val server =
            LocalWebMapServer(
                datasetInfo = datasetInfo,
                styleJson = requireNotNull(mapPackage.styleJson),
                pmtilesPath = mapPath,
            )
        return try {
            DesktopDiagnostics.info("jcef", "Creating browser client dataset=${datasetInfo.id}")
            val client = app.createClient()
            installDiagnostics(client)
            val browser = client.createBrowser(server.indexUrl, false, false)
            DesktopDiagnostics.info("jcef", "Browser created indexUrl=${server.indexUrl}")
            WebMapSession(server = server, client = client, browser = browser)
        } catch (failure: Throwable) {
            server.close()
            throw failure
        }
    }

    override fun close() {
        DesktopDiagnostics.info("jcef", "Disposing CefApp")
        runCatching { app.dispose() }
            .onFailure { failure -> DesktopDiagnostics.warn("jcef", "CefApp dispose failed", failure) }
    }

    private fun installDiagnostics(client: CefClient) {
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    browser: CefBrowser,
                    level: CefSettings.LogSeverity,
                    message: String,
                    source: String,
                    line: Int,
                ): Boolean {
                    DesktopDiagnostics.info(
                        "web.console",
                        "level=$level source=$source line=$line message=$message",
                    )
                    return true
                }
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    DesktopDiagnostics.info(
                        "web.load",
                        "loading=$isLoading url=${browser.url}",
                    )
                }

                override fun onLoadEnd(
                    browser: CefBrowser,
                    frame: CefFrame,
                    httpStatusCode: Int,
                ) {
                    DesktopDiagnostics.info(
                        "web.load",
                        "completed status=$httpStatusCode url=${browser.url}",
                    )
                }

                override fun onLoadError(
                    browser: CefBrowser,
                    frame: CefFrame,
                    errorCode: CefLoadHandler.ErrorCode,
                    errorText: String,
                    failedUrl: String,
                ) {
                    DesktopDiagnostics.warn(
                        "web.load",
                        "failed code=$errorCode text=$errorText url=$failedUrl",
                    )
                }
            },
        )
    }

    companion object {
        fun create(): CefRuntime {
            val runtimeRoot =
                Paths.get(System.getProperty("user.home"), ".osmapdigger", "runtime")
                    .toAbsolutePath()
            val installDir = runtimeRoot.resolve("jcef")
            val cacheDir = runtimeRoot.resolve("jcef-cache")
            Files.createDirectories(installDir)
            Files.createDirectories(cacheDir)

            DesktopDiagnostics.info(
                "jcef",
                "Preparing runtime installDir=$installDir rootCachePath=$cacheDir thread=${Thread.currentThread().name}",
            )

            val builder = CefAppBuilder()
            builder.setInstallDir(installDir.toFile())
            builder.getCefSettings().windowless_rendering_enabled = false
            builder.getCefSettings().root_cache_path = cacheDir.toString()
            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
            val app = builder.build()
            DesktopDiagnostics.info("jcef", "CefApp initialized thread=${Thread.currentThread().name}")
            return CefRuntime(app)
        }
    }
}

private class WebMapSession(
    private val server: LocalWebMapServer,
    private val client: CefClient,
    private val browser: CefBrowser,
) : Closeable {
    val component =
        JPanel(BorderLayout()).apply {
            add(browser.uiComponent, BorderLayout.CENTER)
        }

    private var lastStateJson: String? = null
    private var lastSelectedId: String? = null

    fun update(
        results: List<Settlement>,
        selected: Settlement?,
    ) {
        val stateJson = server.updateState(results = results, selected = selected)
        val selectedId = selected?.id
        if (stateJson == lastStateJson && selectedId == lastSelectedId) {
            return
        }

        val focusSelected = selectedId != null && selectedId != lastSelectedId
        lastStateJson = stateJson
        lastSelectedId = selectedId

        val script =
            "window.osmapdiggerApplyState && window.osmapdiggerApplyState($stateJson, $focusSelected);"
        runCatching { browser.executeJavaScript(script, browser.url, 0) }
            .onFailure { failure ->
                DesktopDiagnostics.warn("web.state", "Could not apply map state", failure)
            }
    }

    override fun close() {
        DesktopDiagnostics.info("map.session", "Closing browser session url=${browser.url}")
        runCatching { browser.close(true) }
            .onFailure { failure -> DesktopDiagnostics.warn("map.session", "Browser close failed", failure) }
        runCatching { client.dispose() }
            .onFailure { failure -> DesktopDiagnostics.warn("map.session", "CefClient dispose failed", failure) }
        server.close()
    }
}
