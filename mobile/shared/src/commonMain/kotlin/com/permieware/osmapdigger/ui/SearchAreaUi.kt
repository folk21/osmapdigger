package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.*
import com.permieware.osmapdigger.presentation.SettlementPlaceTypeResolver
import com.permieware.osmapdigger.search.SettlementSearchService
import kotlinx.coroutines.launch

@Composable
internal fun CenterSelector(
    settlementSearch: SettlementSearchService,
    center: Settlement?,
    onCenterChanged: (Settlement?) -> Unit,
    mapPickerActive: Boolean = false,
    onMapPickerToggle: (() -> Unit)? = null,
    mapPickerEnabled: Boolean = false,
    radiusText: String,
    onRadiusChanged: (String) -> Unit,
    radiusError: String?,
    settlementDisplayName: (Settlement) -> String = { it.name },
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val centerDisplayName = center?.let(settlementDisplayName)
    var suggestions by remember { mutableStateOf<List<SettlementSearchMatch>>(emptyList()) }

    LaunchedEffect(center?.id, centerDisplayName) {
        if (centerDisplayName != null) {
            query = centerDisplayName
        }
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.searchAreaOptional, style = MaterialTheme.typography.titleSmall)

            if (center != null) {
                AssistChip(
                    onClick = {
                        query = ""
                        onCenterChanged(null)
                    },
                    label = { Text(strings.center(settlementDisplayName(center))) },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    suggestions = emptyList()
                    if (center != null && value != centerDisplayName) {
                        onCenterChanged(null)
                    }
                },
                label = { Text(strings.centerSettlementOptional) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val found =
                                if (query.isBlank()) emptyList() else settlementSearch.find(query)
                            val exactMatches = found.filter { it.kind == SettlementMatchKind.EXACT }
                            if (exactMatches.size == 1) {
                                val exact = exactMatches.single().settlement
                                onCenterChanged(exact)
                                query = settlementDisplayName(exact)
                                suggestions = emptyList()
                            } else {
                                suggestions = found
                            }
                        }
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.findCenter)
                }

                if (onMapPickerToggle != null) {
                    OutlinedButton(
                        onClick = onMapPickerToggle,
                        enabled = mapPickerEnabled || mapPickerActive,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (mapPickerActive) strings.cancelCenterPick else strings.pickCenterOnMap)
                    }
                }
            }

            if (mapPickerActive) {
                Text(
                    strings.mapPickHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            suggestions.take(6).forEach { match ->
                TextButton(
                    onClick = {
                        onCenterChanged(match.settlement)
                        suggestions = emptyList()
                        query = settlementDisplayName(match.settlement)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        val displayName = settlementDisplayName(match.settlement)
                        Text(strings.useAsCenter(displayName))
                        val details =
                            listOfNotNull(
                                match.matchedName.takeIf { it != displayName },
                                SettlementPlaceTypeResolver.resolve(match.settlement.placeType, language),
                                match.kind.name.lowercase().takeIf { match.kind == SettlementMatchKind.FUZZY },
                            ).joinToString(" · ")
                        if (details.isNotBlank()) {
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = onRadiusChanged,
                    label = { Text(strings.radiusOptional) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = center != null,
                    isError = radiusError != null,
                    supportingText = {
                        val message = radiusError ?: when {
                            center == null -> strings.selectCenterFirst
                            radiusText.trim().toDoubleOrNull() == 0.0 -> strings.zeroRadiusHint
                            else -> null
                        }
                        if (message != null) {
                            Text(message)
                        }
                    },
                )
                if (radiusText.isNotBlank()) {
                    TextButton(
                        onClick = { onRadiusChanged("") },
                        enabled = center != null,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(strings.clear)
                    }
                }
            }
        }
    }
}
