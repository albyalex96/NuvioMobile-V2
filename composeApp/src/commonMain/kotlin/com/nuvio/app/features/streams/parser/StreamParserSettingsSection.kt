package com.nuvio.app.features.streams.parser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_animations_description
import nuvio.composeapp.generated.resources.stream_parser_animations_title
import nuvio.composeapp.generated.resources.stream_parser_enabled_description
import nuvio.composeapp.generated.resources.stream_parser_enabled_title
import nuvio.composeapp.generated.resources.stream_parser_player_badges_description
import nuvio.composeapp.generated.resources.stream_parser_player_badges_title
import nuvio.composeapp.generated.resources.stream_parser_quality_filter_all
import nuvio.composeapp.generated.resources.stream_parser_quality_filter_description
import nuvio.composeapp.generated.resources.stream_parser_quality_filter_none
import nuvio.composeapp.generated.resources.stream_parser_quality_filter_show_all
import nuvio.composeapp.generated.resources.stream_parser_quality_filter_title
import nuvio.composeapp.generated.resources.stream_parser_section_title
import nuvio.composeapp.generated.resources.stream_parser_sort_by_quality_description
import nuvio.composeapp.generated.resources.stream_parser_sort_by_quality_title
import org.jetbrains.compose.resources.stringResource

/** "Stream Parser" section of Settings → Streams. */
internal fun LazyListScope.streamParserSettingsSection(isTablet: Boolean) {
    item {
        val settings by remember {
            StreamParserSettingsRepository.ensureLoaded()
            StreamParserSettingsRepository.uiState
        }.collectAsStateWithLifecycle()

        Spacer(
            modifier = Modifier.height(
                if (isTablet) NuvioTokens.Space.s18 else MaterialTheme.nuvio.spacing.listGap,
            ),
        )

        SettingsSection(
            title = stringResource(Res.string.stream_parser_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.stream_parser_enabled_title),
                    description = stringResource(Res.string.stream_parser_enabled_description),
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = StreamParserSettingsRepository::setEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.stream_parser_animations_title),
                    description = stringResource(Res.string.stream_parser_animations_description),
                    checked = settings.animationsEnabled,
                    enabled = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = StreamParserSettingsRepository::setAnimationsEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.stream_parser_player_badges_title),
                    description = stringResource(Res.string.stream_parser_player_badges_description),
                    checked = settings.playerBadgesEnabled,
                    enabled = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = StreamParserSettingsRepository::setPlayerBadgesEnabled,
                )
                // Independent from the Stream Parser card: sorting also works with the classic card.
                SettingsSwitchRow(
                    title = stringResource(Res.string.stream_parser_sort_by_quality_title),
                    description = stringResource(Res.string.stream_parser_sort_by_quality_description),
                    checked = settings.sortByQuality,
                    isTablet = isTablet,
                    onCheckedChange = StreamParserSettingsRepository::setSortByQuality,
                )
                StreamQualityFilterRow(
                    hiddenQualities = settings.hiddenQualities,
                    isTablet = isTablet,
                )
            }
        }
    }
}

/** "Shown qualities": multi-select menu of the resolutions kept in the stream results. */
@Composable
private fun StreamQualityFilterRow(
    hiddenQualities: Set<StreamParserResolution>,
    isTablet: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    val qualities = StreamParserResolution.entries
    val summary = when {
        hiddenQualities.isEmpty() -> stringResource(Res.string.stream_parser_quality_filter_all)
        hiddenQualities.size == qualities.size -> stringResource(Res.string.stream_parser_quality_filter_none)
        else -> qualities.filterNot { it in hiddenQualities }.joinToString(", ") { it.label }
    }

    Box {
        SettingsNavigationRow(
            title = stringResource(Res.string.stream_parser_quality_filter_title),
            description = stringResource(Res.string.stream_parser_quality_filter_description),
            isTablet = isTablet,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.accent,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = tokens.colors.textMuted,
                    )
                }
            },
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp)
                .heightIn(max = 360.dp),
        ) {
            qualities.forEach { quality ->
                val visible = quality !in hiddenQualities
                DropdownMenuItem(
                    text = { Text(text = quality.label) },
                    leadingIcon = {
                        Checkbox(
                            checked = visible,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = tokens.colors.accent,
                                checkmarkColor = tokens.colors.onAccent,
                            ),
                        )
                    },
                    // The menu stays open so several qualities can be toggled in one go.
                    onClick = { StreamParserSettingsRepository.setQualityVisible(quality, !visible) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.stream_parser_quality_filter_show_all)) },
                enabled = hiddenQualities.isNotEmpty(),
                onClick = {
                    StreamParserSettingsRepository.showAllQualities()
                    expanded = false
                },
            )
        }
    }
}
