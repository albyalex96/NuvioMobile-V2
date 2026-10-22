package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.search.SearchHistoryRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_cloudstream
import nuvio.composeapp.generated.resources.compose_settings_page_media_servers
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.compose_settings_page_top10
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description_appstore
import nuvio.composeapp.generated.resources.settings_content_discovery_cloudstream_description
import nuvio.composeapp.generated.resources.settings_content_discovery_media_servers_description
import nuvio.composeapp.generated.resources.settings_content_discovery_plugins_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_home
import nuvio.composeapp.generated.resources.settings_content_discovery_section_sources
import nuvio.composeapp.generated.resources.settings_content_discovery_section_search
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches_description
import nuvio.composeapp.generated.resources.top10_settings_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
    showPluginsEntry: Boolean,
    onAddonsClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onMediaServersClick: () -> Unit,
    onCloudstreamClick: () -> Unit,
    onTop10Click: () -> Unit,
) {
    item {
        val recentSearchesEnabled by remember {
            SearchHistoryRepository.ensureLoaded()
            SearchHistoryRepository.enabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_content_discovery_recent_searches),
                    description = stringResource(Res.string.settings_content_discovery_recent_searches_description),
                    checked = recentSearchesEnabled,
                    isTablet = isTablet,
                    onCheckedChange = SearchHistoryRepository::setEnabled,
                )
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_sources),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_addons),
                    description = stringResource(
                        if (AppFeaturePolicy.personalMediaAddonCopyEnabled) {
                            Res.string.settings_content_discovery_addons_description_appstore
                        } else {
                            Res.string.settings_content_discovery_addons_description
                        },
                    ),
                    icon = Icons.Rounded.Extension,
                    isTablet = isTablet,
                    onClick = onAddonsClick,
                )
                if (showPluginsEntry) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_plugins),
                        description = stringResource(Res.string.settings_content_discovery_plugins_description),
                        icon = Icons.Rounded.Hub,
                        isTablet = isTablet,
                        onClick = onPluginsClick,
                    )
                }
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_cloudstream),
                    description = stringResource(Res.string.settings_content_discovery_cloudstream_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Cloudstream),
                    isTablet = isTablet,
                    onClick = onCloudstreamClick,
                )
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_media_servers),
                    description = stringResource(Res.string.settings_content_discovery_media_servers_description),
                    icon = Icons.Rounded.Dns,
                    isTablet = isTablet,
                    onClick = onMediaServersClick,
                )
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_home),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_top10),
                    description = stringResource(Res.string.top10_settings_description),
                    icon = Icons.Rounded.Leaderboard,
                    isTablet = isTablet,
                    onClick = onTop10Click,
                )
            }
        }
    }
}
