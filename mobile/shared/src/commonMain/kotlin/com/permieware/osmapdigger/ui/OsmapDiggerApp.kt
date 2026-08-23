package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.runtime.OsmapDiggerRuntime
import com.permieware.osmapdigger.search.FilterSummaryBuilder
import com.permieware.osmapdigger.search.SearchService
import kotlinx.coroutines.launch

/** Shared OsmapDigger application surface used by Android and Desktop hosts. */
@Composable
fun OsmapDiggerApp(
    runtime: OsmapDiggerRuntime?,
    onImportDataset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        if (runtime == null) {
            MissingDatasetScreen(onImportDataset = onImportDataset, modifier = modifier)
        } else {
            LoadedDatasetApp(runtime = runtime, onImportDataset = onImportDataset, modifier = modifier)
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
    onImportDataset: () -> Unit,
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

    LaunchedEffect(runtime) {
        runCatching {
            val info = runtime.repository.datasetInfo()
            val metricDefinitions = runtime.repository.metricDefinitions()
            datasetInfo = info
            definitions = metricDefinitions
            conditions =
                metricDefinitions
                    .filter { it.defaultEnabled }
                    .map { SearchCondition(metricId = it.id) }
        }.onFailure {
            error = it.message ?: it.toString()
        }
    }

    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val request =
        SearchRequest(
            center = center,
            radiusKm = radiusText.toDoubleOrNull(),
            conditions = conditions,
        )
    val summary = FilterSummaryBuilder.build(request, definitionMap)

    val performSearch: () -> Unit = {
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
                    onCenterChanged = { center = it },
                    radiusText = radiusText,
                    onRadiusChanged = { radiusText = it },
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
                MapPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    datasetInfo = datasetInfo,
                    styleJson = runtime.mapPackage.styleJson,
                    results = results,
                    selected = selected?.settlement,
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
                        onCenterChanged = { center = it },
                        radiusText = radiusText,
                        onRadiusChanged = { radiusText = it },
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
                    MapPanel(
                        modifier = Modifier.fillMaxSize(),
                        datasetInfo = datasetInfo,
                        styleJson = runtime.mapPackage.styleJson,
                        results = results,
                        selected = selected?.settlement,
                    )
                }
            }
        }
    }
}
