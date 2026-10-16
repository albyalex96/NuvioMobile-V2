package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.DateFormatPreset
import com.nuvio.app.core.format.EpisodeCodeFormat
import com.nuvio.app.core.format.formatReleaseDate
import com.nuvio.app.core.ui.floatingNavigationGlowSupported
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.isIos
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.tv.TvAppRuntime
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.labelRes
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.collections_header
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.settings_appearance_app_language
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_message
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_title
import nuvio.composeapp.generated.resources.settings_appearance_app_language_sheet_title
import nuvio.composeapp.generated.resources.settings_appearance_app_icon
import nuvio.composeapp.generated.resources.settings_appearance_date_format
import nuvio.composeapp.generated.resources.settings_appearance_date_format_sheet_title
import nuvio.composeapp.generated.resources.settings_date_format_system
import nuvio.composeapp.generated.resources.settings_appearance_episode_code_format
import nuvio.composeapp.generated.resources.settings_appearance_episode_code_format_sheet_title
import nuvio.composeapp.generated.resources.settings_episode_code_format_default
import nuvio.composeapp.generated.resources.settings_appearance_nav_bar_style
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_on
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_off
import nuvio.composeapp.generated.resources.settings_nav_bar_summary
import nuvio.composeapp.generated.resources.settings_nav_bar_sidebar
import nuvio.composeapp.generated.resources.settings_appearance_amoled_black
import nuvio.composeapp.generated.resources.settings_appearance_amoled_description
import nuvio.composeapp.generated.resources.settings_appearance_amoled_surfaces
import nuvio.composeapp.generated.resources.settings_appearance_amoled_surfaces_description
import nuvio.composeapp.generated.resources.settings_appearance_continue_watching_description
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass_description
import nuvio.composeapp.generated.resources.settings_appearance_poster_customization_description
import nuvio.composeapp.generated.resources.settings_appearance_section_detail_page
import nuvio.composeapp.generated.resources.settings_appearance_section_display
import nuvio.composeapp.generated.resources.settings_appearance_section_home
import nuvio.composeapp.generated.resources.settings_appearance_section_streams
import nuvio.composeapp.generated.resources.settings_appearance_section_theme
import nuvio.composeapp.generated.resources.settings_content_discovery_collections_description
import nuvio.composeapp.generated.resources.settings_content_discovery_homescreen_description
import nuvio.composeapp.generated.resources.settings_content_discovery_meta_screen_description
import nuvio.composeapp.generated.resources.compose_settings_root_streams_description
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState

internal fun LazyListScope.appearanceSettingsContent(
    isTablet: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    amoledEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    amoledSurfacesEnabled: Boolean,
    onAmoledSurfacesToggle: (Boolean) -> Unit,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    onLiquidGlassNativeTabBarToggle: (Boolean) -> Unit,
    appIconState: AppIconSettingsState,
    onAppIconSelected: (AppIconOption) -> Unit,
    onAppIconFailureDismissed: () -> Unit,
    selectedAppLanguage: AppLanguage,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    selectedNavBarStyle: NavBarStyle,
    onNavBarStyleSelected: (NavBarStyle) -> Unit,
    onHomescreenClick: () -> Unit,
    onMetaScreenClick: () -> Unit,
    onStreamsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onContinueWatchingClick: () -> Unit,
    onPosterCustomizationClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_theme),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                AppearanceThemePicker(
                    isTablet = isTablet,
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                )
            }
        }
    }
    item {
        var showLanguageSheet by remember { mutableStateOf(false) }
        var showLanguageRestartDialog by remember { mutableStateOf(false) }
        val layoutDirection = LocalLayoutDirection.current
        var showNavBarStyleSheet by remember { mutableStateOf(false) }
        var showAppIconPicker by remember { mutableStateOf(false) }
        var showDateFormatSheet by remember { mutableStateOf(false) }
        val dateFormat by ThemeSettingsRepository.dateFormat.collectAsStateWithLifecycle()
        var showEpisodeCodeFormatSheet by remember { mutableStateOf(false) }
        val episodeCodeFormat by ThemeSettingsRepository.episodeCodeFormat.collectAsStateWithLifecycle()
        val navBarStyleAvailable = !isIos
        val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val effectiveNavBarStyle = if (isTablet) NavBarStyle.COMPACT else selectedNavBarStyle
        val topNavBarEnabled by ThemeSettingsRepository.topNavBarEnabled.collectAsStateWithLifecycle()
        val topNavBarStyle by ThemeSettingsRepository.topNavBarStyle.collectAsStateWithLifecycle()
        val liveTvUiState by remember {
            LiveTvRepository.ensureLoaded()
            LiveTvRepository.uiState
        }.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_display),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_amoled_black),
                    description = stringResource(Res.string.settings_appearance_amoled_description),
                    checked = amoledEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onAmoledToggle,
                )
                if (amoledEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_appearance_amoled_surfaces),
                        description = stringResource(Res.string.settings_appearance_amoled_surfaces_description),
                        checked = amoledSurfacesEnabled,
                        isTablet = isTablet,
                        onCheckedChange = onAmoledSurfacesToggle,
                    )
                }
                if (liquidGlassNativeTabBarSupported) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_appearance_liquid_glass),
                        description = stringResource(Res.string.settings_appearance_liquid_glass_description),
                        checked = liquidGlassNativeTabBarEnabled,
                        isTablet = isTablet,
                        onCheckedChange = onLiquidGlassNativeTabBarToggle,
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_icon),
                    description = stringResource(appIconState.selected.labelResource),
                    enabled = appIconState.pending == null,
                    isTablet = isTablet,
                    trailingContent = {
                        AppIconThumbnail(
                            icon = appIconState.selected,
                            modifier = Modifier.size(if (isTablet) 44.dp else 40.dp),
                            cornerRadius = if (isTablet) 11.dp else 10.dp,
                        )
                    },
                    onClick = {
                        onAppIconFailureDismissed()
                        showAppIconPicker = true
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_language),
                    description = stringResource(selectedAppLanguage.labelRes),
                    isTablet = isTablet,
                    onClick = { showLanguageSheet = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_date_format),
                    description = dateFormatOptionLabel(dateFormat),
                    isTablet = isTablet,
                    onClick = { showDateFormatSheet = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_episode_code_format),
                    description = episodeCodeFormatOptionLabel(episodeCodeFormat),
                    isTablet = isTablet,
                    onClick = { showEpisodeCodeFormatSheet = true },
                )
                if (navBarStyleAvailable) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_nav_bar_style),
                        description = if (TvAppRuntime.isTvApp) {
                            if (topNavBarEnabled) {
                                stringResource(
                                    Res.string.settings_nav_bar_summary,
                                    stringResource(topNavBarStyle.labelRes),
                                    stringResource(if (glowEnabled) Res.string.settings_nav_bar_glow_on else Res.string.settings_nav_bar_glow_off),
                                )
                            } else {
                                stringResource(Res.string.settings_nav_bar_sidebar)
                            }
                        } else if (floatingNavigationGlowSupported && effectiveNavBarStyle != NavBarStyle.CLASSIC) {
                            stringResource(
                                Res.string.settings_nav_bar_summary,
                                stringResource(effectiveNavBarStyle.labelRes),
                                stringResource(if (glowEnabled) Res.string.settings_nav_bar_glow_on else Res.string.settings_nav_bar_glow_off),
                            )
                        } else {
                            stringResource(effectiveNavBarStyle.labelRes)
                        },
                        isTablet = isTablet,
                        onClick = { showNavBarStyleSheet = true },
                    )
                }
            }
        }

        if (showLanguageSheet) {
            AppearanceLanguageBottomSheet(
                selectedLanguage = selectedAppLanguage,
                onLanguageSelected = {
                    onAppLanguageSelected(it)
                    showLanguageSheet = false
                    val newLayoutDirection = if (it.isRightToLeft()) LayoutDirection.Rtl else LayoutDirection.Ltr
                    if (isIos && newLayoutDirection != layoutDirection) showLanguageRestartDialog = true
                },
                onDismiss = { showLanguageSheet = false },
            )
        }

        if (showDateFormatSheet) {
            AppearanceOptionBottomSheet(
                title = stringResource(Res.string.settings_appearance_date_format_sheet_title),
                options = DateFormatPreset.entries,
                selected = dateFormat,
                optionLabel = { dateFormatOptionLabel(it) },
                onSelected = ThemeSettingsRepository::setDateFormat,
                onDismiss = { showDateFormatSheet = false },
            )
        }

        if (showEpisodeCodeFormatSheet) {
            AppearanceOptionBottomSheet(
                title = stringResource(Res.string.settings_appearance_episode_code_format_sheet_title),
                options = EpisodeCodeFormat.entries,
                selected = episodeCodeFormat,
                optionLabel = { episodeCodeFormatOptionLabel(it) },
                onSelected = ThemeSettingsRepository::setEpisodeCodeFormat,
                onDismiss = { showEpisodeCodeFormatSheet = false },
            )
        }

        NuvioStatusModal(
            title = stringResource(Res.string.settings_appearance_app_language_restart_title),
            message = stringResource(Res.string.settings_appearance_app_language_restart_message),
            isVisible = showLanguageRestartDialog,
            onConfirm = { showLanguageRestartDialog = false },
        )

        if (showAppIconPicker) {
            AppIconPicker(
                isTablet = isTablet,
                state = appIconState,
                onSelected = onAppIconSelected,
                onDismiss = {
                    onAppIconFailureDismissed()
                    showAppIconPicker = false
                },
            )
        }

        if (navBarStyleAvailable && showNavBarStyleSheet) {
            NavigationBarSettingsSheet(
                isTablet = isTablet,
                selectedStyle = effectiveNavBarStyle,
                onStyleSelected = onNavBarStyleSelected,
                glowEnabled = glowEnabled,
                onGlowChanged = ThemeSettingsRepository::setNavBarGlowEnabled,
                showLiveTv = liveTvUiState.showInNavigation,
                onDismiss = { showNavBarStyleSheet = false },
            )
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_home),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_homescreen),
                    description = stringResource(Res.string.settings_content_discovery_homescreen_description),
                    isTablet = isTablet,
                    onClick = onHomescreenClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.collections_header),
                    description = stringResource(Res.string.settings_content_discovery_collections_description),
                    isTablet = isTablet,
                    onClick = onCollectionsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_continue_watching),
                    description = stringResource(Res.string.settings_appearance_continue_watching_description),
                    isTablet = isTablet,
                    onClick = onContinueWatchingClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_poster_customization),
                    description = stringResource(Res.string.settings_appearance_poster_customization_description),
                    isTablet = isTablet,
                    onClick = onPosterCustomizationClick,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_streams),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_streams),
                    description = stringResource(Res.string.compose_settings_root_streams_description),
                    isTablet = isTablet,
                    onClick = onStreamsClick,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_detail_page),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_meta_screen),
                    description = stringResource(Res.string.settings_content_discovery_meta_screen_description),
                    isTablet = isTablet,
                    onClick = onMetaScreenClick,
                )
            }
        }
    }
}

private data class AppLanguageSheetOption(
    val language: AppLanguage,
    val labelRes: StringResource,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceLanguageBottomSheet(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val options = remember {
        AppLanguage.entries.map { language ->
            AppLanguageSheetOption(
                language = language,
                labelRes = language.labelRes,
            )
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_app_language_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            items(options) { option ->
                NuvioBottomSheetActionRow(
                    title = stringResource(option.labelRes),
                    selected = option.language == selectedLanguage,
                    onClick = {
                        onLanguageSelected(option.language)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (option.language == selectedLanguage) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

private const val DATE_FORMAT_SAMPLE_ISO = "2026-10-14"

/** "System default (14 October 2026)" or "DD/MM/YYYY (14/10/2026)". */
@Composable
private fun dateFormatOptionLabel(preset: DateFormatPreset): String {
    val name = preset.patternLabel ?: stringResource(Res.string.settings_date_format_system)
    val sample = formatReleaseDate(DATE_FORMAT_SAMPLE_ISO, includeYear = true, preset = preset)
    return "$name ($sample)"
}

/** "Default (S1 E2)" or "1x2". */
@Composable
private fun episodeCodeFormatOptionLabel(format: EpisodeCodeFormat): String =
    format.sample ?: stringResource(
        Res.string.settings_episode_code_format_default,
        stringResource(Res.string.compose_player_episode_code_full, 1, 2),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AppearanceOptionBottomSheet(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            items(options) { option ->
                NuvioBottomSheetActionRow(
                    title = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        onSelected(option)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
