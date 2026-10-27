package com.nuvio.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.deeplink.ContinueWatchingLaunchAction
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import com.nuvio.app.features.watchprogress.ContinueWatchingSortMode
import com.nuvio.app.tv.TvSettingsNavigator
import com.nuvio.app.tv.TvSettingsPage
import com.nuvio.app.tv.TvSettingsSearchItem
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvRowCardFocusBorder
import com.nuvio.tv.ui.tvFocusable

/** Sections of NuvioTV's layout settings that exist in this app, in the same order. */
private enum class TvLayoutSection(val title: Int, val description: Int, val icon: ImageVector) {
    HomeLayout(R.string.tv_layout_section_home, R.string.tv_layout_section_home_desc, Icons.Filled.Dashboard),
    HomeContent(R.string.tv_layout_section_content, R.string.tv_layout_section_content_desc, Icons.Filled.ViewAgenda),
    ContinueWatching(
        R.string.tv_layout_section_continue_watching,
        R.string.tv_layout_section_continue_watching_desc,
        Icons.Filled.History,
    ),
    Launcher(R.string.tv_layout_section_launcher, R.string.tv_layout_section_launcher_desc, Icons.Filled.Home),
    FocusedPoster(R.string.tv_layout_section_focused, R.string.tv_layout_section_focused_desc, Icons.Filled.CenterFocusStrong),
    PosterCard(R.string.tv_layout_section_card_style, R.string.tv_layout_section_card_style_desc, Icons.Filled.RoundedCorner),
    CardDepth(R.string.tv_layout_section_card_depth, R.string.tv_layout_section_card_depth_desc, Icons.Filled.ViewInAr),
    DetailPage(R.string.tv_layout_section_detail, R.string.tv_layout_section_detail_desc, Icons.Filled.Description),
    Streams(R.string.tv_layout_section_streams, R.string.tv_layout_section_streams_desc, Icons.AutoMirrored.Filled.ViewList),
}

/** Settings -> Appearance -> TV layout, ported from NuvioTV's "Layout settings". */
val TvLayoutSettingsPage = TvSettingsPage(
    content = { navigator -> tvLayoutSettingsContent(navigator) },
    searchEntries = { tvLayoutSearchEntries() },
)

private fun LazyListScope.tvLayoutSettingsContent(navigator: TvSettingsNavigator) {
    TvLayoutSection.entries.forEach { section ->
        item(key = "tv-layout-${section.name}") {
            var expanded by rememberSaveable(section.name) { mutableStateOf(false) }
            TvSettingsCollapsibleSection(
                title = stringResource(section.title),
                description = stringResource(section.description),
                icon = section.icon,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            ) {
                SectionContent(section, navigator)
            }
        }
    }
}

@Composable
private fun SectionContent(section: TvLayoutSection, navigator: TvSettingsNavigator) {
    val settings = rememberTvLayoutSettings()
    val repository = TvLayoutSettingsRepository
    when (section) {
        TvLayoutSection.HomeLayout -> {
            HeroSettings()
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_fullscreen_hero_backdrop),
                subtitle = stringResource(R.string.tv_layout_fullscreen_hero_backdrop_sub),
                checked = settings.heroFullScreenBackdrop,
                onCheckedChange = repository::setHeroFullScreenBackdrop,
            )
            // The glass blur needs RenderEffect (Android 12+); older devices always get the solid bar.
            if (TvTopBarBlurSupported) {
                TvSettingsToggleRow(
                    title = stringResource(R.string.tv_layout_top_bar_blur),
                    subtitle = stringResource(R.string.tv_layout_top_bar_blur_sub),
                    checked = settings.topBarBlur,
                    onCheckedChange = repository::setTopBarBlur,
                )
            }
        }

        TvLayoutSection.HomeContent -> {
            val home by HomeCatalogSettingsRepository.uiState.collectAsState()
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_poster_labels),
                subtitle = stringResource(R.string.tv_layout_poster_labels_sub),
                checked = settings.posterLabels,
                onCheckedChange = repository::setPosterLabels,
            )
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_catalog_type),
                subtitle = stringResource(R.string.tv_layout_catalog_type_sub),
                checked = home.showCatalogType,
                onCheckedChange = HomeCatalogSettingsRepository::setShowCatalogType,
            )
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_hide_unreleased),
                subtitle = stringResource(R.string.tv_layout_hide_unreleased_sub),
                checked = home.hideUnreleasedContent,
                onCheckedChange = HomeCatalogSettingsRepository::setHideUnreleasedContent,
            )
            TvSettingsNote(stringResource(R.string.tv_layout_profile_note))
            TvSettingsActionRow(
                title = stringResource(R.string.tv_layout_more_options),
                subtitle = stringResource(R.string.tv_layout_home_more_sub),
                leadingIcon = Icons.Filled.Tune,
                onClick = navigator.openHomescreen,
            )
        }

        TvLayoutSection.ContinueWatching -> ContinueWatchingSection(settings, navigator)

        TvLayoutSection.Launcher -> {
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_launcher_cw_enabled),
                subtitle = stringResource(R.string.tv_launcher_cw_enabled_sub),
                checked = settings.launcherContinueWatching,
                onCheckedChange = repository::setLauncherContinueWatching,
            )
            if (settings.launcherContinueWatching) {
                TvSettingsOptionRow(
                    title = stringResource(R.string.tv_launcher_open_action),
                    selected = settings.launcherOpenAction,
                    options = listOf(
                        TvPresetOption(stringResource(R.string.tv_launcher_action_menu), ContinueWatchingLaunchAction.Menu),
                        TvPresetOption(stringResource(R.string.tv_launcher_action_play), ContinueWatchingLaunchAction.Play),
                        TvPresetOption(stringResource(R.string.tv_launcher_action_sources), ContinueWatchingLaunchAction.ChooseSource),
                        TvPresetOption(stringResource(R.string.tv_launcher_action_details), ContinueWatchingLaunchAction.Details),
                    ),
                    onSelected = repository::setLauncherOpenAction,
                )
            }
            TvSettingsNote(stringResource(R.string.tv_launcher_note))
        }

        TvLayoutSection.FocusedPoster -> {
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_expand_poster),
                subtitle = stringResource(R.string.tv_layout_expand_poster_sub),
                checked = settings.expandFocusedPoster,
                onCheckedChange = repository::setExpandFocusedPoster,
            )
            if (settings.expandFocusedPoster) {
                TvSettingsSliderRow(
                    title = stringResource(R.string.tv_layout_expand_delay),
                    subtitle = stringResource(R.string.tv_layout_expand_delay_sub),
                    value = settings.expandDelaySeconds,
                    valueText = stringResource(R.string.tv_layout_seconds, settings.expandDelaySeconds),
                    range = TvExpandDelayRange,
                    onValueChange = repository::setExpandDelaySeconds,
                )
            }
        }

        TvLayoutSection.PosterCard -> {
            val widthLabels = listOf(
                R.string.tv_layout_preset_compact,
                R.string.tv_layout_preset_dense,
                R.string.tv_layout_preset_standard,
                R.string.tv_layout_preset_balanced,
                R.string.tv_layout_preset_comfort,
                R.string.tv_layout_preset_large,
            ).map { stringResource(it) }
            val radiusLabels = listOf(
                R.string.tv_layout_preset_sharp,
                R.string.tv_layout_preset_subtle,
                R.string.tv_layout_preset_classic,
                R.string.tv_layout_preset_rounded,
                R.string.tv_layout_preset_pill,
            ).map { stringResource(it) }
            TvSettingsOptionRow(
                title = stringResource(R.string.tv_layout_card_width),
                selected = settings.posterWidthDp,
                options = TvPosterWidthPresets.zip(widthLabels) { value, label -> TvPresetOption(label, value) },
                onSelected = repository::setPosterWidth,
            )
            TvSettingsOptionRow(
                title = stringResource(R.string.tv_layout_card_radius),
                selected = settings.cornerRadiusDp,
                options = TvCornerRadiusPresets.zip(radiusLabels) { value, label -> TvPresetOption(label, value) },
                onSelected = repository::setCornerRadius,
            )
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_landscape_posters),
                subtitle = stringResource(R.string.tv_layout_landscape_posters_sub),
                checked = settings.landscapePosters,
                onCheckedChange = repository::setLandscapePosters,
            )
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_layout_always_show_landscape_clearlogo),
                subtitle = stringResource(R.string.tv_layout_always_show_landscape_clearlogo_sub),
                checked = settings.alwaysShowLandscapeClearlogo,
                onCheckedChange = repository::setAlwaysShowLandscapeClearlogo,
            )
            TvSettingsResetButton(
                text = stringResource(R.string.tv_layout_reset_default),
                onClick = repository::resetPosterCardStyle,
            )
        }

        TvLayoutSection.CardDepth -> CardDepthSection()

        TvLayoutSection.DetailPage -> TvSettingsActionRow(
            title = stringResource(R.string.tv_layout_open_page),
            subtitle = stringResource(R.string.tv_layout_section_detail_desc),
            leadingIcon = Icons.Filled.Tune,
            onClick = navigator.openMetaScreen,
        )

        TvLayoutSection.Streams -> TvSettingsActionRow(
            title = stringResource(R.string.tv_layout_open_page),
            subtitle = stringResource(R.string.tv_layout_section_streams_desc),
            leadingIcon = Icons.Filled.Tune,
            onClick = navigator.openStreams,
        )
    }
}

/**
 * Hero carousel (NuvioTV "Show hero section" + "Hero catalogs"): the shared home settings, synced
 * with the profile. At most [HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT] catalogs.
 */
@Composable
private fun HeroSettings() {
    val home by HomeCatalogSettingsRepository.uiState.collectAsState()
    TvSettingsToggleRow(
        title = stringResource(R.string.tv_layout_show_hero),
        subtitle = stringResource(R.string.tv_layout_show_hero_sub),
        checked = home.heroEnabled,
        onCheckedChange = HomeCatalogSettingsRepository::setHeroEnabled,
    )
    val catalogs = home.items.filter { !it.isCollection }
    if (!home.heroEnabled || catalogs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.tv_layout_hero_catalogs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.nuvio.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = stringResource(R.string.tv_layout_hero_catalogs_sub, HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.nuvio.colors.textMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
        val firstChip = remember { FocusRequester() }
        LazyRow(
            modifier = Modifier.focusRestorer(firstChip).focusGroup(),
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(catalogs, key = { _, item -> item.key }) { index, item ->
                val title = item.customTitle.ifBlank { item.defaultTitle }
                TvSettingsChoiceChip(
                    label = if (item.addonName.isBlank()) title else "$title · ${item.addonName}",
                    selected = item.heroSourceEnabled,
                    onClick = { HomeCatalogSettingsRepository.setHeroSourceEnabled(item.key, !item.heroSourceEnabled) },
                    modifier = if (index == 0) Modifier.focusRequester(firstChip) else Modifier,
                )
            }
        }
    }
    TvSettingsNote(stringResource(R.string.tv_layout_profile_note))
}

@Composable
private fun ContinueWatchingSection(settings: TvLayoutSettings, navigator: TvSettingsNavigator) {
    LaunchedEffect(Unit) { ContinueWatchingPreferencesRepository.ensureLoaded() }
    val prefs by ContinueWatchingPreferencesRepository.uiState.collectAsState()
    TvSettingsToggleRow(
        title = stringResource(R.string.tv_layout_cw_enabled),
        subtitle = stringResource(R.string.tv_layout_cw_enabled_sub),
        checked = prefs.isVisible,
        onCheckedChange = ContinueWatchingPreferencesRepository::setVisible,
    )
    if (!prefs.isVisible) return
    Text(
        text = stringResource(R.string.tv_layout_cw_card_style),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.nuvio.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ContinueWatchingSectionStyle.entries.forEach { style ->
            CwStyleCard(
                style = style,
                selected = settings.continueWatchingStyle == style,
                onClick = { TvLayoutSettingsRepository.setContinueWatchingStyle(style) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (settings.continueWatchingStyle != ContinueWatchingSectionStyle.Poster) {
        TvSettingsToggleRow(
            title = stringResource(R.string.tv_layout_use_episode_thumbnails_cw),
            subtitle = stringResource(R.string.tv_layout_use_episode_thumbnails_cw_sub),
            checked = prefs.useEpisodeThumbnails,
            onCheckedChange = ContinueWatchingPreferencesRepository::setUseEpisodeThumbnails,
        )
        TvSettingsToggleRow(
            title = stringResource(R.string.tv_layout_blur_cw_next_up),
            subtitle = stringResource(R.string.tv_layout_blur_cw_next_up_sub),
            checked = prefs.blurNextUp,
            onCheckedChange = ContinueWatchingPreferencesRepository::setBlurNextUp,
        )
    }
    TvSettingsToggleRow(
        title = stringResource(R.string.tv_layout_next_up_furthest_episode),
        subtitle = stringResource(R.string.tv_layout_next_up_furthest_episode_sub),
        checked = prefs.upNextFromFurthestEpisode,
        onCheckedChange = ContinueWatchingPreferencesRepository::setUpNextFromFurthestEpisode,
    )
    TvSettingsToggleRow(
        title = stringResource(R.string.tv_layout_show_unaired_next_up),
        subtitle = stringResource(R.string.tv_layout_show_unaired_next_up_sub),
        checked = prefs.showUnairedNextUp,
        onCheckedChange = ContinueWatchingPreferencesRepository::setShowUnairedNextUp,
    )
    TvSettingsOptionRow(
        title = stringResource(R.string.tv_layout_cw_sort_mode),
        selected = prefs.sortMode,
        options = listOf(
            TvPresetOption(stringResource(R.string.tv_layout_cw_sort_default), ContinueWatchingSortMode.DEFAULT),
            TvPresetOption(stringResource(R.string.tv_layout_cw_sort_streaming), ContinueWatchingSortMode.STREAMING_STYLE),
            TvPresetOption(stringResource(R.string.tv_layout_cw_sort_split_upcoming), ContinueWatchingSortMode.SPLIT_UPCOMING),
        ),
        onSelected = ContinueWatchingPreferencesRepository::setSortMode,
    )
    TvSettingsNote(stringResource(R.string.tv_layout_profile_note))
    TvSettingsActionRow(
        title = stringResource(R.string.tv_layout_more_options),
        subtitle = stringResource(R.string.tv_layout_cw_more_sub),
        leadingIcon = Icons.Filled.Tune,
        onClick = navigator.openContinueWatching,
    )
}

@Composable
private fun CardDepthSection() {
    LaunchedEffect(Unit) { CardDepthStyleRepository.ensureLoaded() }
    val depth by CardDepthStyleRepository.uiState.collectAsState()
    TvSettingsToggleRow(
        title = stringResource(R.string.tv_layout_card_depth_enabled),
        subtitle = stringResource(R.string.tv_layout_card_depth_enabled_sub),
        checked = depth.enabled,
        onCheckedChange = CardDepthStyleRepository::setEnabled,
    )
    if (depth.enabled) {
        // Presets of NuvioTV's card depth section.
        TvSettingsOptionRow(
            title = stringResource(R.string.tv_layout_card_depth_edge),
            selected = depth.edgeStrength,
            options = listOf(
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_edge_subtle), 28),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_edge_balanced), 42),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_edge_bold), 56),
            ),
            onSelected = CardDepthStyleRepository::setEdgeStrength,
        )
        TvSettingsOptionRow(
            title = stringResource(R.string.tv_layout_card_depth_sheen),
            selected = depth.sheenStrength,
            options = listOf(
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_sheen_off), 0),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_sheen_soft), 10),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_sheen_bright), 16),
            ),
            onSelected = CardDepthStyleRepository::setSheenStrength,
        )
        TvSettingsOptionRow(
            title = stringResource(R.string.tv_layout_card_depth_edge_coverage),
            selected = depth.edgeCoverage,
            options = listOf(
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_coverage_top), 0),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_coverage_half), 50),
                TvPresetOption(stringResource(R.string.tv_layout_card_depth_coverage_full), 100),
            ),
            onSelected = CardDepthStyleRepository::setEdgeCoverage,
        )
        TvSettingsResetButton(
            text = stringResource(R.string.tv_layout_reset_default),
            onClick = CardDepthStyleRepository::resetToDefaults,
        )
    }
    TvSettingsNote(stringResource(R.string.tv_layout_profile_note))
}

/** Style choice with a small drawing of the card (NuvioTV `CwStyleCard`). */
@Composable
private fun CwStyleCard(
    style: ContinueWatchingSectionStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .tvFocusable(
                shape = shape,
                onClick = onClick,
                focusedScale = 1f,
                borderWidth = TvRowCardFocusBorder,
                onFocusChange = { focused = it },
            )
            .background(colors.background)
            .then(if (selected && !focused) Modifier.border(1.dp, colors.focusRing, shape) else Modifier)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            CwStylePreview(style)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.focusRing,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                )
            }
            Text(
                text = stringResource(
                    when (style) {
                        ContinueWatchingSectionStyle.Card -> R.string.tv_layout_cw_card_style_card
                        ContinueWatchingSectionStyle.Wide -> R.string.tv_layout_cw_card_style_wide
                        ContinueWatchingSectionStyle.Poster -> R.string.tv_layout_cw_card_style_poster
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected || focused) colors.textPrimary else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CwStylePreview(style: ContinueWatchingSectionStyle) {
    val colors = MaterialTheme.nuvio.colors
    val art = colors.textMuted.copy(alpha = 0.35f)
    val line = colors.textMuted.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(6.dp)
    when (style) {
        ContinueWatchingSectionStyle.Card -> Box(
            modifier = Modifier.fillMaxHeight(0.8f).aspectRatio(16f / 9f).clip(shape).background(art),
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.width(40.dp).height(5.dp).clip(shape).background(line))
                Box(modifier = Modifier.width(64.dp).height(3.dp).clip(shape).background(colors.accent))
            }
        }
        ContinueWatchingSectionStyle.Wide -> Row(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.55f).clip(shape).background(colors.surfaceCard),
        ) {
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).background(art))
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.width(44.dp).height(5.dp).clip(shape).background(line))
                Box(modifier = Modifier.width(28.dp).height(4.dp).clip(shape).background(line))
                Box(modifier = Modifier.width(52.dp).height(3.dp).clip(shape).background(colors.accent))
            }
        }
        ContinueWatchingSectionStyle.Poster -> Column(horizontalAlignment = Alignment.Start) {
            Box(modifier = Modifier.height(72.dp).aspectRatio(2f / 3f).clip(shape).background(art)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(5.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(shape)
                        .background(colors.accent),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.width(36.dp).height(5.dp).clip(shape).background(line))
        }
    }
}

/** Search index of the page: every option with its section, as shown above. */
@Composable
private fun tvLayoutSearchEntries(): List<TvSettingsSearchItem> {
    fun entries(section: Int, vararg rows: Pair<Int, Int?>): List<Triple<Int, Int?, Int>> =
        rows.map { (title, description) -> Triple(title, description, section) }
    val rows = entries(
        R.string.tv_layout_section_home,
        R.string.tv_layout_show_hero to R.string.tv_layout_show_hero_sub,
        R.string.tv_layout_hero_catalogs to null,
        R.string.tv_layout_fullscreen_hero_backdrop to R.string.tv_layout_fullscreen_hero_backdrop_sub,
        *if (TvTopBarBlurSupported) {
            arrayOf<Pair<Int, Int?>>(R.string.tv_layout_top_bar_blur to R.string.tv_layout_top_bar_blur_sub)
        } else {
            emptyArray<Pair<Int, Int?>>()
        },
    ) + entries(
        R.string.tv_layout_section_content,
        R.string.tv_layout_poster_labels to R.string.tv_layout_poster_labels_sub,
        R.string.tv_layout_catalog_type to R.string.tv_layout_catalog_type_sub,
        R.string.tv_layout_hide_unreleased to R.string.tv_layout_hide_unreleased_sub,
    ) + entries(
        R.string.tv_layout_section_continue_watching,
        R.string.tv_layout_cw_enabled to R.string.tv_layout_cw_enabled_sub,
        R.string.tv_layout_cw_card_style to null,
        R.string.tv_layout_use_episode_thumbnails_cw to R.string.tv_layout_use_episode_thumbnails_cw_sub,
        R.string.tv_layout_blur_cw_next_up to R.string.tv_layout_blur_cw_next_up_sub,
        R.string.tv_layout_next_up_furthest_episode to R.string.tv_layout_next_up_furthest_episode_sub,
        R.string.tv_layout_show_unaired_next_up to R.string.tv_layout_show_unaired_next_up_sub,
        R.string.tv_layout_cw_sort_mode to R.string.tv_layout_cw_sort_mode_sub,
    ) + entries(
        R.string.tv_layout_section_launcher,
        R.string.tv_launcher_cw_enabled to R.string.tv_launcher_cw_enabled_sub,
        R.string.tv_launcher_open_action to R.string.tv_launcher_open_action_sub,
    ) + entries(
        R.string.tv_layout_section_focused,
        R.string.tv_layout_expand_poster to R.string.tv_layout_expand_poster_sub,
        R.string.tv_layout_expand_delay to R.string.tv_layout_expand_delay_sub,
    ) + entries(
        R.string.tv_layout_section_card_style,
        R.string.tv_layout_card_width to R.string.tv_layout_section_card_style_desc,
        R.string.tv_layout_card_radius to R.string.tv_layout_section_card_style_desc,
        R.string.tv_layout_landscape_posters to R.string.tv_layout_landscape_posters_sub,
        R.string.tv_layout_always_show_landscape_clearlogo to R.string.tv_layout_always_show_landscape_clearlogo_sub,
    ) + entries(
        R.string.tv_layout_section_card_depth,
        R.string.tv_layout_card_depth_enabled to R.string.tv_layout_card_depth_enabled_sub,
        R.string.tv_layout_card_depth_edge to null,
        R.string.tv_layout_card_depth_sheen to null,
        R.string.tv_layout_card_depth_edge_coverage to null,
    )
    return rows.map { (title, description, section) ->
        TvSettingsSearchItem(
            key = "$section-$title",
            title = stringResource(title),
            description = description?.let { stringResource(it) }.orEmpty(),
            section = stringResource(section),
        )
    }
}
