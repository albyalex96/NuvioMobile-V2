package com.nuvio.app.features.streams.parser

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stream_parser_animations_description
import nuvio.composeapp.generated.resources.stream_parser_animations_title
import nuvio.composeapp.generated.resources.stream_parser_enabled_description
import nuvio.composeapp.generated.resources.stream_parser_enabled_title
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
                // Independent from the Stream Parser card: sorting also works with the classic card.
                SettingsSwitchRow(
                    title = stringResource(Res.string.stream_parser_sort_by_quality_title),
                    description = stringResource(Res.string.stream_parser_sort_by_quality_description),
                    checked = settings.sortByQuality,
                    isTablet = isTablet,
                    onCheckedChange = StreamParserSettingsRepository::setSortByQuality,
                )
            }
        }
    }
}
