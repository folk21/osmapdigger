package com.permieware.osmapdigger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.permieware.osmapdigger.external.ExternalSearchProvider
import com.permieware.osmapdigger.external.ExternalSearchProviderTermsUpdate
import com.permieware.osmapdigger.external.resolveQueryTerms

/** Shared provider-terms editor; persistence remains owned by the injected settings repository. */
@Composable
internal fun ExternalSearchTermsSettings(
    providers: List<ExternalSearchProvider>,
    datasetTerms: String,
    onSave: (List<ExternalSearchProviderTermsUpdate>) -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val normalizedDatasetTerms = datasetTerms.trim()
    val editorStates = remember(providers, normalizedDatasetTerms) {
        mutableStateMapOf<String, String>().apply {
            providers.forEach { provider ->
                put(provider.settingsKey(), provider.resolveQueryTerms(normalizedDatasetTerms))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.externalSearchSettings, style = MaterialTheme.typography.titleMedium)
                Text(strings.datasetSearchTerms(normalizedDatasetTerms), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClose) { Text(strings.close) }
        }
        Text(strings.externalSearchTermsHint, style = MaterialTheme.typography.bodySmall)
        TextButton(
            enabled = providers.isNotEmpty(),
            onClick = {
                onSave(
                    providers.map { provider ->
                        ExternalSearchProviderTermsUpdate(
                            providerId = provider.id,
                            countryCode = provider.countryCode,
                            queryTermsOverride = editorStates[provider.settingsKey()].orEmpty().trim(),
                        )
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.saveSearchSettings)
        }
        TextButton(
            enabled = providers.isNotEmpty(),
            onClick = {
                providers.forEach { provider ->
                    editorStates[provider.settingsKey()] = normalizedDatasetTerms
                }
            },
        ) {
            Text(strings.setDefaultSearchTerms)
        }

        providers.forEachIndexed { index, provider ->
            val key = provider.settingsKey()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(provider.title, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = editorStates[key].orEmpty(),
                    onValueChange = { value -> editorStates[key] = value },
                    label = { Text(strings.customSearchTerms) },
                    supportingText = { Text(strings.emptyCustomSearchTermsHint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (index != providers.lastIndex) HorizontalDivider()
        }

    }
}

private fun ExternalSearchProvider.settingsKey(): String = "$id|${countryCode.orEmpty()}"
