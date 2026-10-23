package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.features.streams.STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES
import com.nuvio.app.features.streams.StreamPriorityProvider
import com.nuvio.app.features.streams.StreamProviderCategory
import com.nuvio.app.features.streams.StreamProviderPriorityConfig
import com.nuvio.app.features.streams.orderedProviders
import com.nuvio.app.features.streams.withVisibleOrder
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Categories offered in the settings: plugins and CloudStream exist only when plugins are enabled. */
internal fun availableProviderCategories(pluginsEnabled: Boolean): List<StreamProviderCategory> =
    if (pluginsEnabled) StreamProviderCategory.entries else listOf(StreamProviderCategory.ADDONS)

@Composable
internal fun providerCategoryLabel(category: StreamProviderCategory): String = when (category) {
    StreamProviderCategory.ADDONS -> stringResource(Res.string.settings_playback_provider_category_addons)
    StreamProviderCategory.PLUGINS -> stringResource(Res.string.settings_playback_provider_category_plugins)
    StreamProviderCategory.CLOUDSTREAM -> stringResource(Res.string.settings_playback_provider_category_cloudstream)
}

@Composable
internal fun providerCategoriesSummary(config: StreamProviderPriorityConfig, pluginsEnabled: Boolean): String {
    val labels = availableProviderCategories(pluginsEnabled)
        .filter { it in config.categories }
        .map { providerCategoryLabel(it) }
    return labels.joinToString(", ").ifBlank { stringResource(Res.string.settings_playback_provider_priority_sources_none) }
}

/** Providers listed in the order dialog: only the categories included in the priority. */
internal fun priorityDialogProviders(
    config: StreamProviderPriorityConfig,
    catalog: List<StreamPriorityProvider>,
    pluginsEnabled: Boolean,
): List<StreamPriorityProvider> {
    val categories = availableProviderCategories(pluginsEnabled).filter { it in config.categories }.toSet()
    return config.orderedProviders(catalog).filter { it.category in categories }
}

@Composable
internal fun StreamProviderPriorityOrderDialog(
    config: StreamProviderPriorityConfig,
    providers: List<StreamPriorityProvider>,
    onSave: (StreamProviderPriorityConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var ordered by remember(config, providers) { mutableStateOf(providers) }
    var disabled by remember(config) { mutableStateOf(config.disabled) }

    fun saveAndClose() {
        // Providers of excluded categories keep their saved place after the visible ones.
        onSave(config.withVisibleOrder(ordered.map { it.key }).copy(disabled = disabled))
        onDismiss()
    }

    fun move(from: Int, to: Int) {
        if (to !in ordered.indices) return
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
    }

    DialogSurface(
        onDismissRequest = ::saveAndClose,
        modifier = Modifier.heightIn(max = 620.dp),
        title = stringResource(Res.string.settings_playback_provider_priority_order),
        message = stringResource(Res.string.settings_playback_provider_priority_order_hint),
    ) {
        if (ordered.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_playback_no_items_available),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = ordered.size, key = { ordered[it].key }) { index ->
                    val provider = ordered[index]
                    val enabled = provider.key !in disabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DialogOption(
                            text = "${index + 1}. ${provider.name}",
                            description = listOfNotNull(providerCategoryLabel(provider.category), provider.sourceLabel)
                                .joinToString(" · "),
                            selected = enabled,
                            role = Role.Checkbox,
                            onClick = {
                                disabled = if (enabled) disabled + provider.key else disabled - provider.key
                            },
                            modifier = Modifier.weight(1f),
                        )
                        NuvioIconActionButton(
                            icon = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(Res.string.addons_move_up),
                            tint = if (index > 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            onClick = { move(index, index - 1) },
                        )
                        NuvioIconActionButton(
                            icon = Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(Res.string.addons_move_down),
                            tint = if (index < ordered.lastIndex) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            onClick = { move(index, index + 1) },
                        )
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = ::saveAndClose,
            )
        }
    }
}

@Composable
internal fun StreamProviderCategoriesDialog(
    config: StreamProviderPriorityConfig,
    pluginsEnabled: Boolean,
    onCategoryToggled: (StreamProviderCategory, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_provider_priority_sources),
        message = stringResource(Res.string.settings_playback_provider_priority_sources_description),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableProviderCategories(pluginsEnabled).forEach { category ->
                val isSelected = category in config.categories
                DialogOption(
                    text = providerCategoryLabel(category),
                    selected = isSelected,
                    role = Role.Checkbox,
                    onClick = { onCategoryToggled(category, !isSelected) },
                )
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

/** Slider over [STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES], laid out like the stream timeout slider. */
@Composable
internal fun StreamProviderPriorityTimeoutSlider(
    timeoutSeconds: Int,
    isTablet: Boolean,
    onTimeoutSelected: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val timeoutLabel = if (timeoutSeconds == Int.MAX_VALUE) {
        stringResource(Res.string.settings_playback_timeout_unlimited)
    } else {
        stringResource(Res.string.settings_playback_timeout_seconds, timeoutSeconds)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 18.dp else 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(Res.string.settings_playback_provider_priority_timeout),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.settings_playback_provider_priority_timeout_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ValueBox(text = timeoutLabel, modifier = Modifier.wrapContentWidth())
        }
        val timeoutIndex = STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES.indexOf(timeoutSeconds).coerceAtLeast(0)
        val maxIndex = STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES.lastIndex.toFloat()
        var sliderValue by remember(timeoutIndex) { mutableFloatStateOf(timeoutIndex.toFloat()) }
        var lastHapticStep by remember(timeoutIndex) { mutableStateOf(timeoutIndex.toFloat()) }
        Slider(
            value = sliderValue,
            onValueChange = {
                val snapped = snapToStep(it, 1f)
                sliderValue = snapped
                if (snapped != lastHapticStep) {
                    lastHapticStep = snapped
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onValueChangeFinished = {
                val index = sliderValue.toInt().coerceIn(STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES.indices)
                onTimeoutSelected(STREAM_PROVIDER_PRIORITY_TIMEOUT_VALUES[index])
            },
            valueRange = 0f..maxIndex,
            steps = calculateSteps(0f, maxIndex, 1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
