package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.preferences.UserPreferences
import com.permieware.osmapdigger.preferences.UserPreferencesRepository
import com.permieware.osmapdigger.preferences.UserPreferencesRestorer
import com.permieware.osmapdigger.runtime.OsmapDiggerRuntime
import com.permieware.osmapdigger.search.FilterSummaryBuilder
import com.permieware.osmapdigger.search.SearchService
import kotlinx.coroutines.launch

/** Shared OsmapDigger application surface used by Android and Desktop hosts. */
@Composable
fun OsmapDiggerApp(
    runtime: OsmapDiggerRuntime?,
    userPreferences: UserPreferencesRepository,
    onImportDataset: () -> Unit,
    platformMapSurface: PlatformMapSurface? = null,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        if (runtime == null) {
            MissingDatasetScreen(onImportDataset = onImportDataset, modifier = modifier)
        } else {
            LoadedDatasetApp(
                runtime = runtime,
                userPreferences = userPreferences,
                onImportDataset = onImportDataset,
                platformMapSurface = platformMapSurface,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MissingDatasetScreen(
    onImportDataset: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp)) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("OsmapDigger", style = MaterialTheme.typography.headlineMedium)
                Text("No local dataset is installed.")
                Text("Generate a .omd.zip with the Python Geo Builder, then import it here.")
                Button(onClick = onImportDataset) {
                    Text("Import dataset")
                }
            }
        }
    }
}

/**
 * Coordinate shared dataset/filter/result state after platform adapters are available.
 * Wide Desktop layouts use side-by-side search/map panes; narrow Android layouts reuse
 * the same state through Search/Map tabs.
 */
@Composable
private fun LoadedDatasetApp(
    runtime: OsmapDiggerRuntime,
    userPreferences: UserPreferencesRepository,
    onImportDataset: () -> Unit,
    platformMapSurface: PlatformMapSurface?,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var datasetInfo by remember { mutableStateOf<DatasetInfo?>(null) }
    var definitions by remember { mutableStateOf<List<MetricDefinition>>(emptyList()) }
    var conditions by remember { mutableStateOf<List<SearchCondition>>(emptyList()) }
    var results by remember { mutableStateOf<List<Settlement>>(emptyList()) }
    var selected by remember { mutableStateOf<SettlementDetails?>(null) }
    var center by remember { mutableStateOf<Settlement?>(null) }
    var radiusText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var preferencesReady by remember { mutableStateOf(false) }

    LaunchedEffect(runtime) {
        preferencesReady = false
        datasetInfo = null
        definitions = emptyList()
        conditions = emptyList()
        results = emptyList()
        selected = null
        center = null
        radiusText = ""
        error = null

        runCatching {
            val info = runtime.repository.datasetInfo()
            val metricDefinitions = runtime.repository.metricDefinitions()
            val defaults =
                metricDefinitions
                    .filter { it.defaultEnabled }
                    .map { SearchCondition(metricId = it.id) }
            val savedPreferences = runCatching { userPreferences.load() }.getOrNull()
            val restored =
                UserPreferencesRestorer.restore(
                    preferences = savedPreferences,
                    datasetId = info.id,
                    definitions = metricDefinitions,
                    resolveSettlement = { settlementId ->
                        runCatching { runtime.repository.details(settlementId).settlement }.getOrNull()
                    },
                )

            datasetInfo = info
            definitions = metricDefinitions
            conditions = restored?.conditions ?: defaults
            center = restored?.center
            radiusText = restored?.radiusKm?.toString() ?: ""
            preferencesReady = true
        }.onFailure {
            error = it.message ?: it.toString()
        }
    }

    LaunchedEffect(
        runtime,
        userPreferences,
        preferencesReady,
        datasetInfo?.id,
        center?.id,
        center?.name,
        radiusText,
        conditions,
    ) {
        val info = datasetInfo
        if (!preferencesReady || info == null) {
            return@LaunchedEffect
        }

        runCatching {
            userPreferences.save(
                UserPreferences(
                    datasetId = info.id,
                    centerSettlementId = center?.id,
                    centerSettlementName = center?.name,
                    radiusKm = center?.let { parsePositiveRadiusKm(radiusText) },
                    conditions = conditions,
                ),
            )
        }.onFailure {
            error = "Could not save user preferences: ${it.message ?: it}"
        }
    }

    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val parsedRadius = parsePositiveRadiusKm(radiusText)
    val radiusError =
        when {
            radiusText.isBlank() -> null
            center == null -> "Select a center settlement before setting a radius."
            parsedRadius == null -> "Radius must be a positive number."
            else -> null
        }
    val request =
        SearchRequest(
            center = center,
            radiusKm = if (radiusError == null) parsedRadius else null,
            conditions = conditions,
        )
    val summary = FilterSummaryBuilder.build(request, definitionMap)

    val performSearch: () -> Unit = {
        if (radiusError != null) {
            error = radiusError
        } else {
            scope.launch {
                running = true
                error = null
                runCatching { SearchService(runtime.repository).search(request) }
                    .onSuccess {
                        results = it
                        selected = null
                    }
                    .onFailure { error = it.message ?: it.toString() }
                running = false
            }
        }
    }

    val selectSettlement: (Settlement) -> Unit = { settlement ->
        scope.launch {
            runCatching { runtime.repository.details(settlement.id) }
                .onSuccess { selected = it }
                .onFailure { error = it.message ?: it.toString() }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SearchPane(
                    modifier = Modifier.width(430.dp).fillMaxHeight(),
                    datasetInfo = datasetInfo,
                    definitions = definitions,
                    conditions = conditions,
                    onConditionsChanged = { conditions = it },
                    center = center,
                    onCenterChanged = {
                        center = it
                        if (it == null) {
                            radiusText = ""
                        }
                    },
                    radiusText = radiusText,
                    onRadiusChanged = { radiusText = it },
                    radiusError = radiusError,
                    results = results,
                    selected = selected,
                    summary = summary,
                    running = running,
                    error = error,
                    repository = runtime.repository,
                    onSearch = performSearch,
                    onSelect = selectSettlement,
                    onImportDataset = onImportDataset,
                    externalLinks = runtime.externalLinks,
                )
                RuntimeMapPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    datasetInfo = datasetInfo,
                    mapPackage = runtime.mapPackage,
                    results = results,
                    selected = selected?.settlement,
                    platformMapSurface = platformMapSurface,
                )
            }
        } else {
            var page by remember { mutableStateOf(0) }
            Column(Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = page) {
                    Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("Search") })
                    Tab(selected = page == 1, onClick = { page = 1 }, text = { Text("Map") })
                }
                if (page == 0) {
                    SearchPane(
                        modifier = Modifier.fillMaxSize(),
                        datasetInfo = datasetInfo,
                        definitions = definitions,
                        conditions = conditions,
                        onConditionsChanged = { conditions = it },
                        center = center,
                        onCenterChanged = {
                            center = it
                            if (it == null) {
                                radiusText = ""
                            }
                        },
                        radiusText = radiusText,
                        onRadiusChanged = { radiusText = it },
                        radiusError = radiusError,
                        results = results,
                        selected = selected,
                        summary = summary,
                        running = running,
                        error = error,
                        repository = runtime.repository,
                        onSearch = performSearch,
                        onSelect = selectSettlement,
                        onImportDataset = onImportDataset,
                        externalLinks = runtime.externalLinks,
                    )
                } else {
                    RuntimeMapPanel(
                        modifier = Modifier.fillMaxSize(),
                        datasetInfo = datasetInfo,
                        mapPackage = runtime.mapPackage,
                        results = results,
                        selected = selected?.settlement,
                        platformMapSurface = platformMapSurface,
                    )
                }
            }
        }
    }
}


@Composable
private fun RuntimeMapPanel(
    modifier: Modifier,
    datasetInfo: DatasetInfo?,
    mapPackage: com.permieware.osmapdigger.runtime.MapPackage,
    results: List<Settlement>,
    selected: Settlement?,
    platformMapSurface: PlatformMapSurface?,
) {
    if (platformMapSurface != null) {
        platformMapSurface.Render(
            modifier = modifier,
            datasetInfo = datasetInfo,
            mapPackage = mapPackage,
            results = results,
            selected = selected,
        )
    } else {
        MapPanel(
            modifier = modifier,
            datasetInfo = datasetInfo,
            styleJson = mapPackage.styleJson,
            results = results,
            selected = selected,
        )
    }
}

private fun parsePositiveRadiusKm(value: String): Double? =
    value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
