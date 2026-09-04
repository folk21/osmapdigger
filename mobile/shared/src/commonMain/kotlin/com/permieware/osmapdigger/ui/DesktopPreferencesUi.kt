package com.permieware.osmapdigger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.domain.MetricDefinition
import com.permieware.osmapdigger.domain.PreferredDirection
import com.permieware.osmapdigger.preferences.EffectiveMetricPreference
import com.permieware.osmapdigger.preferences.MetricPreferenceOverride
import com.permieware.osmapdigger.presentation.MetricDisplayNameResolver
import com.permieware.osmapdigger.presentation.NumberFormatter
import com.permieware.osmapdigger.presentation.UiStrings
import kotlin.math.roundToInt

@Composable
internal fun PreferencesSection(
    definitions: List<MetricDefinition>,
    effectivePreferences: List<EffectiveMetricPreference>,
    preferenceOverrides: List<MetricPreferenceOverride>,
    onEnabledChanged: (String, Boolean) -> Unit,
    onWeightChanged: (String, Int) -> Unit,
    onThresholdsChanged: (String, Double, Double) -> Unit,
    onReset: (String) -> Unit,
) {
    val strings = LocalUiStrings.current
    val language = LocalUiLanguage.current
    val definitionMap = remember(definitions) { definitions.associateBy { it.id } }
    val overriddenIds = remember(preferenceOverrides) { preferenceOverrides.mapTo(hashSetOf()) { it.metricId } }
    var expandedMetricId by remember { mutableStateOf<String?>(null) }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.preferences, style = MaterialTheme.typography.titleSmall)
            if (effectivePreferences.isEmpty()) {
                Text(
                    strings.noPreferenceDefaults,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            effectivePreferences.forEach { effective ->
                val definition = definitionMap[effective.metricId]
                PreferenceRow(
                    title = definition?.let { MetricDisplayNameResolver.resolve(it, language) } ?: effective.metricId,
                    unit = definition?.unit.orEmpty(),
                    effective = effective,
                    overridden = effective.metricId in overriddenIds,
                    expanded = expandedMetricId == effective.metricId,
                    onExpandedChange = { expanded ->
                        expandedMetricId = if (expanded) effective.metricId else null
                    },
                    onEnabledChanged = { onEnabledChanged(effective.metricId, it) },
                    onWeightChanged = { onWeightChanged(effective.metricId, it) },
                    onThresholdsChanged = { target, limit ->
                        onThresholdsChanged(effective.metricId, target, limit)
                    },
                    onReset = { onReset(effective.metricId) },
                )
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    unit: String,
    effective: EffectiveMetricPreference,
    overridden: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onThresholdsChanged: (Double, Double) -> Unit,
    onReset: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val preference = effective.preference
    var targetText by remember(effective.metricId, preference.targetValue) {
        mutableStateOf(preference.targetValue.toString())
    }
    var limitText by remember(effective.metricId, preference.limitValue) {
        mutableStateOf(preference.limitValue.toString())
    }
    val target = targetText.toDoubleOrNull()
    val limit = limitText.toDoubleOrNull()
    val thresholdsValid =
        target != null && limit != null && target.isFinite() && limit.isFinite() &&
            when (preference.direction) {
                PreferredDirection.LOWER -> target < limit
                PreferredDirection.HIGHER -> target > limit
                PreferredDirection.NEUTRAL -> false
            }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = effective.enabled,
                onCheckedChange = onEnabledChanged,
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    preferenceTargetSummary(preference.direction, preference.targetValue, preference.limitValue, unit, strings),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                "W ${preference.weight}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        if (expanded) {
            Column(
                Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { value ->
                            targetText = value
                            val parsedTarget = value.toDoubleOrNull()
                            val parsedLimit = limitText.toDoubleOrNull()
                            if (validThresholds(preference.direction, parsedTarget, parsedLimit)) {
                                onThresholdsChanged(parsedTarget!!, parsedLimit!!)
                            }
                        },
                        label = { Text(targetLabel(preference.direction, strings)) },
                        suffix = { if (unit.isNotBlank()) Text(unit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !thresholdsValid,
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { value ->
                            limitText = value
                            val parsedTarget = targetText.toDoubleOrNull()
                            val parsedLimit = value.toDoubleOrNull()
                            if (validThresholds(preference.direction, parsedTarget, parsedLimit)) {
                                onThresholdsChanged(parsedTarget!!, parsedLimit!!)
                            }
                        },
                        label = { Text(limitLabel(preference.direction, strings)) },
                        suffix = { if (unit.isNotBlank()) Text(unit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !thresholdsValid,
                    )
                }
                if (!thresholdsValid) {
                    Text(
                        thresholdError(preference.direction, strings),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Text(strings.weight(preference.weight), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = preference.weight.toFloat(),
                    onValueChange = { onWeightChanged(it.roundToInt().coerceIn(1, 10)) },
                    valueRange = 1f..10f,
                    steps = 8,
                )

                if (overridden) {
                    TextButton(onClick = onReset) { Text(strings.resetDatasetDefault) }
                }
            }
        }
    }
}


private fun preferenceTargetSummary(
    direction: PreferredDirection,
    target: Double,
    limit: Double,
    unit: String,
    strings: UiStrings,
): String {
    val suffix = unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return when (direction) {
        PreferredDirection.LOWER ->
            strings.bestLowerSummary("${NumberFormatter.compact(target)}$suffix", "${NumberFormatter.compact(limit)}$suffix")
        PreferredDirection.HIGHER ->
            strings.bestHigherSummary("${NumberFormatter.compact(target)}$suffix", "${NumberFormatter.compact(limit)}$suffix")
        PreferredDirection.NEUTRAL -> strings.notScoreable
    }
}

private fun validThresholds(
    direction: PreferredDirection,
    target: Double?,
    limit: Double?,
): Boolean =
    target != null && limit != null && target.isFinite() && limit.isFinite() &&
        when (direction) {
            PreferredDirection.LOWER -> target < limit
            PreferredDirection.HIGHER -> target > limit
            PreferredDirection.NEUTRAL -> false
        }

private fun targetLabel(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.bestLower
        PreferredDirection.HIGHER -> strings.bestHigher
        PreferredDirection.NEUTRAL -> strings.target
    }

private fun limitLabel(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.zeroHigher
        PreferredDirection.HIGHER -> strings.zeroLower
        PreferredDirection.NEUTRAL -> strings.limit
    }

private fun thresholdError(direction: PreferredDirection, strings: UiStrings): String =
    when (direction) {
        PreferredDirection.LOWER -> strings.lowerThresholdError
        PreferredDirection.HIGHER -> strings.higherThresholdError
        PreferredDirection.NEUTRAL -> strings.neutralThresholdError
    }

