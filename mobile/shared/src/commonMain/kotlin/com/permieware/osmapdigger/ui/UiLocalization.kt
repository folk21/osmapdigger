package com.permieware.osmapdigger.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.permieware.osmapdigger.presentation.MetricFilterPresentationText
import com.permieware.osmapdigger.presentation.UiLanguage
import com.permieware.osmapdigger.presentation.UiLocalization
import com.permieware.osmapdigger.presentation.UiStrings
import com.permieware.osmapdigger.search.FilterSummaryText

val LocalUiLanguage = staticCompositionLocalOf { UiLocalization.defaultLanguage }
val LocalUiStrings = staticCompositionLocalOf { UiLocalization.strings(UiLocalization.defaultLanguage) }
val LocalUiLanguageSetter = staticCompositionLocalOf<(UiLanguage) -> Unit> { {} }

@Composable
internal fun LanguageSelector() {
    val language = LocalUiLanguage.current
    val strings = LocalUiStrings.current
    val setLanguage = LocalUiLanguageSetter.current
    var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    TextButton(onClick = { expanded.value = true }) {
        Text("${strings.languageLabel}: ${language.displayName}")
    }
    DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
        UiLanguage.entries.forEach { candidate ->
            DropdownMenuItem(
                text = { Text(candidate.displayName) },
                onClick = {
                    setLanguage(candidate)
                    expanded.value = false
                },
            )
        }
    }
}

internal fun UiStrings.filterSummaryText(): FilterSummaryText =
    FilterSummaryText(
        noFilters = noFiltersSummary,
        findSettlements = findSettlementsPrefix,
        withinCenter = withinCenter,
        aroundCenter = aroundCenter,
        atLeast = atLeast,
        upTo = upTo,
    )

internal fun UiStrings.metricFilterText(): MetricFilterPresentationText =
    MetricFilterPresentationText(
        minDistance = minDistance,
        maxDistance = maxDistance,
        minNumber = minNumber,
        maxNumber = maxNumber,
        minCoverage = minCoverage,
        maxCoverage = maxCoverage,
        minValue = minValue,
        maxValue = maxValue,
        nearestMappedFeature = nearestMappedFeature,
        mappedFeaturesInFixedRadius = mappedFeaturesInFixedRadius,
        mappedAreaCoverage = mappedAreaCoverage,
    )
