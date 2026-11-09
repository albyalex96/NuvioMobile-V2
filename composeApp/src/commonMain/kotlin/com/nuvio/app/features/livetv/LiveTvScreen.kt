package com.nuvio.app.features.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlin.coroutines.cancellation.CancellationException
import com.nuvio.app.tv.tvLongPressKeys
import nuvio.composeapp.generated.resources.live_tv_channel_link_copied
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioPlatformExtraTopPadding
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.tv.LocalTvShell
import com.nuvio.app.tv.TvCenteredScrolling
import com.nuvio.app.tv.tvNotFocusable
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.live_tv_action_back_to_top
import nuvio.composeapp.generated.resources.live_tv_button_continue
import nuvio.composeapp.generated.resources.live_tv_empty_message
import nuvio.composeapp.generated.resources.live_tv_empty_title
import nuvio.composeapp.generated.resources.live_tv_filter_choose_category
import nuvio.composeapp.generated.resources.live_tv_filter_favorites
import nuvio.composeapp.generated.resources.live_tv_group_all_channels
import nuvio.composeapp.generated.resources.live_tv_group_uncategorized
import nuvio.composeapp.generated.resources.live_tv_last_watched_title
import nuvio.composeapp.generated.resources.live_tv_error_no_channels
import nuvio.composeapp.generated.resources.live_tv_error_sources_failed
import nuvio.composeapp.generated.resources.live_tv_load_failed
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_message
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_title
import nuvio.composeapp.generated.resources.live_tv_no_playlist_message
import nuvio.composeapp.generated.resources.live_tv_no_playlist_title
import nuvio.composeapp.generated.resources.live_tv_search_placeholder
import nuvio.composeapp.generated.resources.live_tv_title
import nuvio.composeapp.generated.resources.live_tv_filter_all
import nuvio.composeapp.generated.resources.live_tv_guide_empty_message
import nuvio.composeapp.generated.resources.live_tv_guide_empty_title
import nuvio.composeapp.generated.resources.live_tv_on_demand_empty_message
import nuvio.composeapp.generated.resources.live_tv_on_demand_empty_title
import nuvio.composeapp.generated.resources.live_tv_on_demand_load_failed
import nuvio.composeapp.generated.resources.live_tv_on_demand_movies
import nuvio.composeapp.generated.resources.live_tv_on_demand_search_placeholder
import nuvio.composeapp.generated.resources.live_tv_on_demand_series
import nuvio.composeapp.generated.resources.live_tv_on_demand_unavailable_message
import nuvio.composeapp.generated.resources.live_tv_on_demand_unavailable_title
import nuvio.composeapp.generated.resources.live_tv_sports_empty_message
import nuvio.composeapp.generated.resources.live_tv_sports_empty_title
import nuvio.composeapp.generated.resources.live_tv_sports_filter_live
import nuvio.composeapp.generated.resources.live_tv_sports_filter_reminders
import nuvio.composeapp.generated.resources.live_tv_sports_load_failed
import nuvio.composeapp.generated.resources.live_tv_sports_no_leagues
import com.nuvio.app.core.ui.NuvioToastController
import org.jetbrains.compose.resources.getString
import nuvio.composeapp.generated.resources.live_tv_record_scheduled_toast
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveTvScreen(
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onChannelClick: (LiveTvChannel) -> Unit,
    onVodClick: (LiveTvVodPlayback) -> Unit = {},
    /** Channel menu: plays the channel in the external player. */
    onChannelExternalClick: (LiveTvChannel) -> Unit = {},
    /** Channel menu: plays the channel privately (not saved as last watched, hidden from Discord). */
    onChannelPrivateClick: (LiveTvChannel) -> Unit = {},
) {
    val uiState by remember {
        LiveTvRepository.ensureChannelsLoaded()
        LiveTvRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 320
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(LiveTvChannelFilterMode.All) }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var guideChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val linkCopiedText = stringResource(Res.string.live_tv_channel_link_copied)
    val epgNowMillis = rememberLiveTvEpgNowMillis()
    var section by rememberSaveable { mutableStateOf(LiveTvSection.Channels) }
    val recordings by remember {
        LiveTvRecordingsRepository.ensureLoaded()
        LiveTvRecordingsRepository.recordings
    }.collectAsStateWithLifecycle()
    var manualRecordingChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var gameRecordingKey by rememberSaveable { mutableStateOf<String?>(null) }
    // A reminder notification opens the Sports section.
    val requestedSection by LiveTvSectionRequests.requested.collectAsStateWithLifecycle()
    LaunchedEffect(requestedSection) {
        val target = requestedSection?.let { name -> LiveTvSection.entries.firstOrNull { it.name == name } }
        if (requestedSection != null) LiveTvSectionRequests.consume()
        if (target != null) section = target
    }
    val guideScrollState = remember { LiveTvGuideScrollState() }
    val guidePulse = rememberLiveTvGuidePulse()
    val guideTextMeasurer = rememberLiveTvGuideTextMeasurer()
    val guideWindowStart = remember(epgNowMillis / LIVE_TV_GUIDE_SLOT_MILLIS) { liveTvGuideWindowStart(epgNowMillis) }
    var sportsFilter by rememberSaveable { mutableStateOf(LIVE_TV_SPORTS_FILTER_ALL) }
    var vodKind by rememberSaveable { mutableStateOf(LiveTvVodKind.Movies) }
    var vodCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var vodSearchQuery by rememberSaveable { mutableStateOf("") }
    var seriesSheetItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val sportsState by LiveTvSportsRepository.uiState.collectAsStateWithLifecycle()
    val sportsLeaguesSettings by remember {
        LiveTvSportsSettingsRepository.ensureLoaded()
        LiveTvSportsSettingsRepository.settings
    }.collectAsStateWithLifecycle()
    val onDemandState by LiveTvOnDemandRepository.uiState.collectAsStateWithLifecycle()

    val allChannelsLabel = stringResource(Res.string.live_tv_group_all_channels)
    val favoritesLabel = stringResource(Res.string.live_tv_filter_favorites)
    val uncategorizedLabel = stringResource(Res.string.live_tv_group_uncategorized)
    val chooseCategoryLabel = stringResource(Res.string.live_tv_filter_choose_category)

    val categoryOptions = remember(
        uiState.displayedChannels,
        allChannelsLabel,
        favoritesLabel,
        uncategorizedLabel,
    ) {
        buildLiveTvCategoryFilterOptions(
            channels = uiState.displayedChannels,
            allChannelsLabel = allChannelsLabel,
            favoritesLabel = favoritesLabel,
            uncategorizedLabel = uncategorizedLabel,
        )
    }

    val visibleChannels = remember(
        uiState.displayedChannels,
        uiState.favoriteChannelIds,
        filterMode,
        selectedCategoryName,
        searchQuery,
        uncategorizedLabel,
    ) {
        filterLiveTvChannels(
            channels = uiState.displayedChannels,
            favoriteChannelIds = uiState.favoriteChannelIds,
            filterMode = filterMode,
            selectedCategoryName = selectedCategoryName,
            searchQuery = searchQuery,
            uncategorizedGroupName = uncategorizedLabel,
        )
    }

    val lastWatchedChannel = remember(uiState.displayedChannels, uiState.lastWatchedChannelId) {
        uiState.lastWatchedChannelId?.let { channelId ->
            uiState.displayedChannels.firstOrNull { it.id == channelId }
        }
    }

    val playChannel: (LiveTvChannel) -> Unit = { channel ->
        LiveTvRepository.markChannelWatched(channel)
        onChannelClick(channel)
    }

    // Guide: channels with programmes in the window, favourites first. Opens scrolled to "now".
    // Computed off the main thread: with thousands of channels and days of EPG this blocked the
    // opening of the section. Only while the Guide is shown.
    val guideChannelsResult by produceState<List<LiveTvChannel>?>(
        initialValue = null,
        visibleChannels,
        uiState.epgPrograms,
        guideWindowStart,
        uiState.favoriteChannelIds,
        section == LiveTvSection.Guide,
    ) {
        if (section != LiveTvSection.Guide) return@produceState
        value = withContext(Dispatchers.Default) {
            visibleChannels
                .filter { liveTvGuideHasPrograms(uiState.epgPrograms[it.id], guideWindowStart) }
                .sortedByDescending { it.id in uiState.favoriteChannelIds }
        }
    }
    val guideChannels = guideChannelsResult.orEmpty()
    val density = LocalDensity.current
    val guideNowOffsetPx = remember(epgNowMillis, guideWindowStart, density) {
        val minutesBeforeNow = ((epgNowMillis - guideWindowStart) / 60_000L - 15L).coerceAtLeast(0L)
        with(density) { (LiveTvGuideDpPerMinute * minutesBeforeNow.toFloat()).roundToPx() }
    }
    LaunchedEffect(section, guideWindowStart, guideChannels.isNotEmpty()) {
        if (section != LiveTvSection.Guide || guideChannels.isEmpty()) return@LaunchedEffect
        withTimeoutOrNull(3_000L) {
            snapshotFlow { guideScrollState.maxPx }.first { it > 0f }
        }
        // Instant jump on opening: an animated scroll here ran while the first rows were built.
        guideScrollState.snapTo(guideNowOffsetPx.toFloat())
    }

    // Sports: refreshed while the section is open (every minute with live matches, otherwise every 5).
    LaunchedEffect(section) {
        if (section != LiveTvSection.Sports) return@LaunchedEffect
        while (true) {
            LiveTvSportsRepository.refreshIfStale()
            delay(30_000L)
        }
    }
    val sportsMatches by produceState(
        initialValue = emptyMap<String, List<LiveTvChannel>>(),
        sportsState.events,
        uiState.displayedChannels,
        section == LiveTvSection.Sports,
    ) {
        if (section != LiveTvSection.Sports) return@produceState
        value = withContext(Dispatchers.Default) {
            val normalized = uiState.displayedChannels.map { it to normalizeSportsName(it.name) }
            sportsState.events.associate { event ->
                "${event.league.id}:${event.id}" to matchSportsChannels(event, normalized)
            }
        }
    }
    val sportsLeagues = remember(sportsState.events) {
        val present = sportsState.events.map { it.league.id }.toSet()
        liveTvSportsLeagues.filter { it.id in present }
    }
    val hasLiveSports = sportsState.events.any { it.state == LiveTvSportsState.Live }
    val reminderOptions by remember {
        LiveTvSportsRemindersRepository.ensureLoaded()
        LiveTvSportsRemindersRepository.options
    }.collectAsStateWithLifecycle()
    val sportsReminders by LiveTvSportsRemindersRepository.reminders.collectAsStateWithLifecycle()
    // Bells and the Reminders filter only with reminders turned on (Settings → Sports).
    val remindersActive = reminderOptions.enabled && LiveTvSportsRemindersRepository.isSupported
    val reminderKeys = if (remindersActive) sportsReminders.keys else null
    val hasSportsReminders = remindersActive &&
        sportsState.events.any { liveTvSportsEventKey(it) in sportsReminders }
    val visibleSports = remember(sportsState.events, sportsFilter, sportsReminders) {
        sportsState.events.filter { event ->
            when (sportsFilter) {
                LIVE_TV_SPORTS_FILTER_ALL -> true
                LIVE_TV_SPORTS_FILTER_LIVE -> event.state == LiveTvSportsState.Live
                LIVE_TV_SPORTS_FILTER_REMINDERS -> liveTvSportsEventKey(event) in sportsReminders
                else -> event.league.id == sportsFilter
            }
        }
    }
    LaunchedEffect(sportsLeagues, hasLiveSports, hasSportsReminders, sportsFilter) {
        val valid = sportsFilter == LIVE_TV_SPORTS_FILTER_ALL ||
            (sportsFilter == LIVE_TV_SPORTS_FILTER_LIVE && hasLiveSports) ||
            (sportsFilter == LIVE_TV_SPORTS_FILTER_REMINDERS && hasSportsReminders) ||
            sportsLeagues.any { it.id == sportsFilter }
        if (!valid && sportsState.hasLoaded) sportsFilter = LIVE_TV_SPORTS_FILTER_ALL
    }

    // On Demand: Xtream VOD catalog, loaded the first time the section opens.
    val hasVodProvider = uiState.xtreamSettings.isEnabled && uiState.xtreamSettings.isConfigured
    LaunchedEffect(section, vodKind, hasVodProvider) {
        if (section == LiveTvSection.OnDemand && hasVodProvider) LiveTvOnDemandRepository.ensureLoaded(vodKind)
    }
    val vodCatalog = onDemandState.catalog(vodKind)
    val visibleVod = remember(vodCatalog.catalog.items, vodCategoryId, vodSearchQuery) {
        filterLiveTvVodItems(vodCatalog.catalog.items, vodCategoryId, vodSearchQuery)
    }

    LaunchedEffect(section) {
        Logger.i("LiveTv") { "Section: ${section.name}" }
    }

    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(searchQuery, filterMode, selectedCategoryName, section, vodKind, vodCategoryId, vodSearchQuery, sportsFilter) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(categoryOptions, filterMode, selectedCategoryName) {
        if (filterMode == LiveTvChannelFilterMode.Category && selectedCategoryName != null) {
            val selectedStillExists = categoryOptions.any { option ->
                option.mode == LiveTvChannelFilterMode.Category && option.categoryName == selectedCategoryName
            }
            if (!selectedStillExists) {
                filterMode = LiveTvChannelFilterMode.All
                selectedCategoryName = null
            }
        }
    }

    // TV: the controls are a normal row, not a sticky header. A sticky header covers the channels
    // that the D-pad brings into view at the top of the list (the focus seemed stuck on the search
    // field) and is always the nearest target above any channel, so Up jumped back to it.
    val isTv = LocalTvShell.current != null
    LaunchedEffect(isTv) {
        if (isTv) Logger.d("LiveTv") { "TV layout: channel controls scroll with the list" }
    }

    val tokens = MaterialTheme.nuvio
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenBackground = tokens.colors.background
    val listTopPadding = tokens.spacing.screenTop + statusBarTop + nuvioPlatformExtraTopPadding

    val controlsTopPadding by remember(listTopPadding) {
        derivedStateOf {
            val offset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == ChannelControlsKey }
                ?.offset
                ?: return@derivedStateOf listTopPadding
            (listTopPadding - with(density) { offset.toDp() }).coerceIn(0.dp, listTopPadding)
        }
    }

    TvCenteredScrolling {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val vodColumns = ((maxWidth - LiveTvScreenHorizontalPadding * 2) / LiveTvVodMinCellWidth)
                .toInt()
                .coerceIn(3, 8)
            NuvioScreen(
                modifier = Modifier.fillMaxSize(),
                horizontalPadding = LiveTvScreenHorizontalPadding,
                listState = listState,
            ) {
                item {
                    NuvioScreenHeader(
                        title = stringResource(Res.string.live_tv_title),
                        includeStatusBarPadding = false,
                        actions = {
                            if (uiState.hasPlaylist) {
                                NuvioIconActionButton(
                                    icon = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(Res.string.action_retry),
                                    onClick = {
                                        when (section) {
                                            LiveTvSection.Channels -> LiveTvRepository.refresh()
                                            LiveTvSection.Guide -> LiveTvRepository.refreshEpg()
                                            LiveTvSection.Sports -> LiveTvSportsRepository.refresh()
                                            LiveTvSection.OnDemand -> LiveTvOnDemandRepository.refresh(vodKind)
                                            LiveTvSection.Recordings -> Unit
                                        }
                                    },
                                )
                            }
                        },
                    )
                }

                if (uiState.hasPlaylist) {
                    item(key = "live-tv-sections") {
                        LiveTvSectionTabs(
                            selected = section,
                            sportsLive = hasLiveSports,
                            onSelected = { selected -> section = selected },
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                if (uiState.hasPlaylist && section != LiveTvSection.Channels && section != LiveTvSection.Recordings) {
                    val sectionControls: @Composable () -> Unit = {
                        LiveTvSectionControlsSurface(
                            topPadding = if (isTv) 0.dp else controlsTopPadding,
                            background = screenBackground,
                        ) {
                            when (section) {
                                LiveTvSection.Guide -> if (uiState.channels.isNotEmpty()) {
                                    LiveTvSearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                                    LiveTvFilterPanelRow(
                                        groups = categoryOptions
                                            .filter { option -> option.mode == LiveTvChannelFilterMode.Category }
                                            .map { option -> option.label },
                                        selectedGroup = selectedCategoryName,
                                        favoritesOnly = filterMode == LiveTvChannelFilterMode.Favorites,
                                        allLabel = allChannelsLabel,
                                        favoritesLabel = favoritesLabel,
                                        categoryLabel = chooseCategoryLabel,
                                        onAllSelected = {
                                            filterMode = LiveTvChannelFilterMode.All
                                            selectedCategoryName = null
                                        },
                                        onFavoritesSelected = {
                                            filterMode = LiveTvChannelFilterMode.Favorites
                                            selectedCategoryName = null
                                        },
                                        onGroupSelected = { category ->
                                            filterMode = LiveTvChannelFilterMode.Category
                                            selectedCategoryName = category
                                        },
                                    )
                                    if (guideChannels.isNotEmpty()) {
                                        LiveTvGuideTimeRuler(
                                            scrollState = guideScrollState,
                                            windowStart = guideWindowStart,
                                            nowMillis = epgNowMillis,
                                            pulse = guidePulse,
                                            onJumpToNow = {
                                                coroutineScope.launch { guideScrollState.animateTo(guideNowOffsetPx.toFloat()) }
                                            },
                                            listState = listState,
                                        )
                                    }
                                }

                                LiveTvSection.Sports -> if (sportsState.events.isNotEmpty()) {
                                    LiveTvSportsControls(
                                        allLabel = stringResource(Res.string.live_tv_filter_all),
                                        liveLabel = stringResource(Res.string.live_tv_sports_filter_live),
                                        leagues = sportsLeagues,
                                        hasLive = hasLiveSports,
                                        selectedFilter = sportsFilter,
                                        eventCount = visibleSports.size,
                                        onFilterSelected = { sportsFilter = it },
                                        remindersLabel = if (hasSportsReminders) {
                                            stringResource(Res.string.live_tv_sports_filter_reminders)
                                        } else {
                                            null
                                        },
                                    )
                                }

                                LiveTvSection.OnDemand -> if (hasVodProvider) {
                                    LiveTvSearchField(
                                        query = vodSearchQuery,
                                        onQueryChange = { vodSearchQuery = it },
                                        placeholder = stringResource(Res.string.live_tv_on_demand_search_placeholder),
                                    )
                                    LiveTvOnDemandControls(
                                        kind = vodKind,
                                        moviesLabel = stringResource(Res.string.live_tv_on_demand_movies),
                                        seriesLabel = stringResource(Res.string.live_tv_on_demand_series),
                                        allLabel = stringResource(Res.string.live_tv_filter_all),
                                        categories = vodCatalog.catalog.categories,
                                        selectedCategoryId = vodCategoryId,
                                        itemCount = visibleVod.size,
                                        onKindSelected = { kind ->
                                            if (kind != vodKind) {
                                                vodKind = kind
                                                vodCategoryId = null
                                            }
                                        },
                                        onCategorySelected = { vodCategoryId = it },
                                    )
                                }

                                LiveTvSection.Channels, LiveTvSection.Recordings -> Unit
                            }
                        }
                    }
                    if (isTv) {
                        item(key = ChannelControlsKey) { sectionControls() }
                    } else {
                        stickyHeader(key = ChannelControlsKey) { sectionControls() }
                    }
                }

                if (uiState.hasPlaylist && section == LiveTvSection.Guide && uiState.channels.isNotEmpty()) {
                    when {
                        guideChannels.isNotEmpty() -> liveTvGuideRows(
                            channels = guideChannels,
                            epgPrograms = uiState.epgPrograms,
                            scrollState = guideScrollState,
                            windowStart = guideWindowStart,
                            nowMillis = epgNowMillis,
                            pulse = guidePulse,
                            textMeasurer = guideTextMeasurer,
                            onChannelClick = playChannel,
                            onProgramClick = { channel, program ->
                                if (program.isAiringAt(epgNowMillis)) playChannel(channel) else guideChannelId = channel.id
                            },
                            listState = listState,
                        )

                        uiState.isEpgLoading || guideChannelsResult == null -> liveTvLoadingItem()

                        else -> item(key = "guide-empty") {
                            HomeEmptyStateCard(
                                title = stringResource(
                                    if (visibleChannels.isEmpty()) Res.string.live_tv_no_matching_channels_title else Res.string.live_tv_guide_empty_title,
                                ),
                                message = stringResource(
                                    if (visibleChannels.isEmpty()) Res.string.live_tv_no_matching_channels_message else Res.string.live_tv_guide_empty_message,
                                ),
                            )
                        }
                    }
                } else if (uiState.hasPlaylist && section == LiveTvSection.Sports) {
                    when {
                        sportsState.events.isEmpty() && (sportsState.isLoading || !sportsState.hasLoaded) -> liveTvLoadingItem()

                        sportsState.events.isEmpty() && sportsState.errorMessage != null -> item(key = "sports-error") {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_sports_load_failed),
                                message = sportsState.errorMessage.orEmpty(),
                                actionLabel = stringResource(Res.string.action_retry),
                                onActionClick = LiveTvSportsRepository::refresh,
                            )
                        }

                        visibleSports.isEmpty() -> item(key = "sports-empty") {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_sports_empty_title),
                                message = stringResource(
                                    if (sportsLeaguesSettings.enabledLeagues().isEmpty()) {
                                        Res.string.live_tv_sports_no_leagues
                                    } else {
                                        Res.string.live_tv_sports_empty_message
                                    },
                                ),
                            )
                        }

                        else -> liveTvSportsList(
                            events = visibleSports,
                            matchedChannels = sportsMatches,
                            nowMillis = epgNowMillis,
                            onChannelClick = playChannel,
                            reminderKeys = reminderKeys,
                            onReminderToggle = LiveTvSportsRemindersRepository::toggle,
                            onRecordGame = if (LiveTvRecordingsRepository.isSupported) {
                                { event ->
                                    val channels = sportsMatches[liveTvSportsEventKey(event)].orEmpty()
                                    if (channels.size == 1) {
                                        coroutineScope.launch {
                                            LiveTvRecordingsRepository.scheduleGame(channels.first(), event)?.let {
                                                NuvioToastController.show(getString(Res.string.live_tv_record_scheduled_toast, it.title))
                                            }
                                        }
                                    } else {
                                        gameRecordingKey = liveTvSportsEventKey(event)
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                } else if (uiState.hasPlaylist && section == LiveTvSection.Recordings) {
                    liveTvRecordingsList(
                        recordings = recordings,
                        nowMillis = epgNowMillis,
                        showExactAlarmWarning = !LiveTvRecordingPlatform.canScheduleExactAlarms(),
                        onAllowExactAlarms = LiveTvRecordingPlatform::openExactAlarmSettings,
                        onPlay = { recording ->
                            recording.fileUri?.let { uri ->
                                onVodClick(
                                    LiveTvVodPlayback(
                                        id = recording.id,
                                        title = recording.title,
                                        streamUrl = uri,
                                        headers = emptyMap(),
                                        streamType = null,
                                        posterUrl = recording.channelLogoUrl,
                                    ),
                                )
                            }
                        },
                    )
                } else if (uiState.hasPlaylist && section == LiveTvSection.OnDemand) {
                    when {
                        !hasVodProvider -> item(key = "vod-unavailable") {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_on_demand_unavailable_title),
                                message = stringResource(Res.string.live_tv_on_demand_unavailable_message),
                            )
                        }

                        vodCatalog.catalog.items.isEmpty() && (vodCatalog.isLoading || !vodCatalog.hasLoaded) -> liveTvLoadingItem()

                        vodCatalog.catalog.items.isEmpty() && vodCatalog.errorMessage != null -> item(key = "vod-error") {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_on_demand_load_failed),
                                message = vodCatalog.errorMessage.orEmpty(),
                                actionLabel = stringResource(Res.string.action_retry),
                                onActionClick = { LiveTvOnDemandRepository.refresh(vodKind) },
                            )
                        }

                        visibleVod.isEmpty() -> item(key = "vod-empty") {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_on_demand_empty_title),
                                message = stringResource(Res.string.live_tv_on_demand_empty_message),
                            )
                        }

                        else -> liveTvVodGrid(
                            items = visibleVod,
                            columns = vodColumns,
                            gap = 12.dp,
                            onItemClick = { item ->
                                when (item.kind) {
                                    LiveTvVodKind.Movies -> LiveTvOnDemandRepository.moviePlayback(item)?.let(onVodClick)
                                    LiveTvVodKind.Series -> seriesSheetItemId = item.id
                                }
                            },
                        )
                    }
                } else when {
                    uiState.isLoading && uiState.channels.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.nuvio.colors.accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    !uiState.hasPlaylist -> {
                        item {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_no_playlist_title),
                                message = stringResource(Res.string.live_tv_no_playlist_message),
                            )
                        }
                    }

                    !uiState.errorMessage.isNullOrBlank() && uiState.channels.isEmpty() -> {
                        item {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_load_failed),
                                message = if (uiState.failedSourceNames.isNotEmpty()) {
                                    stringResource(Res.string.live_tv_error_sources_failed, uiState.failedSourceNames.joinToString())
                                } else {
                                    stringResource(Res.string.live_tv_error_no_channels)
                                },
                                actionLabel = stringResource(Res.string.action_retry),
                                onActionClick = LiveTvRepository::refresh,
                            )
                        }
                    }

                    uiState.channels.isEmpty() -> {
                        item {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_empty_title),
                                message = stringResource(Res.string.live_tv_empty_message),
                                actionLabel = stringResource(Res.string.action_retry),
                                onActionClick = LiveTvRepository::refresh,
                            )
                        }
                    }

                    else -> {
                        if (lastWatchedChannel != null) {
                            item(key = "last-watched:${lastWatchedChannel.id}") {
                                LiveTvRecentChannelCard(
                                    channel = lastWatchedChannel,
                                    categoryName = categoryNameForChannel(lastWatchedChannel, uncategorizedLabel),
                                    onContinueClick = { playChannel(lastWatchedChannel) },
                                    programs = uiState.epgPrograms[lastWatchedChannel.id].orEmpty(),
                                    nowMillis = epgNowMillis,
                                    onGuideClick = { guideChannelId = lastWatchedChannel.id },
                                )
                            }
                        }

                        val channelControls: @Composable () -> Unit = {
                            LiveTvChannelControls(
                                topPadding = if (isTv) 0.dp else controlsTopPadding,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                categoryOptions = categoryOptions,
                                selectedCategoryName = selectedCategoryName,
                                filterMode = filterMode,
                                allLabel = allChannelsLabel,
                                favoritesLabel = favoritesLabel,
                                categoryLabel = chooseCategoryLabel,
                                channelCount = visibleChannels.size,
                                background = screenBackground,
                                onAllSelected = {
                                    filterMode = LiveTvChannelFilterMode.All
                                    selectedCategoryName = null
                                },
                                onFavoritesSelected = {
                                    filterMode = LiveTvChannelFilterMode.Favorites
                                    selectedCategoryName = null
                                },
                                onGroupSelected = { category ->
                                    filterMode = LiveTvChannelFilterMode.Category
                                    selectedCategoryName = category
                                },
                            )
                        }
                        if (isTv) {
                            item(key = ChannelControlsKey) { channelControls() }
                        } else {
                            stickyHeader(key = ChannelControlsKey) { channelControls() }
                        }

                        if (visibleChannels.isEmpty()) {
                            item {
                                HomeEmptyStateCard(
                                    title = stringResource(Res.string.live_tv_no_matching_channels_title),
                                    message = stringResource(Res.string.live_tv_no_matching_channels_message),
                                )
                            }
                        } else {
                            liveTvChannelList(
                                channels = visibleChannels,
                                favoriteChannelIds = uiState.favoriteChannelIds,
                                uncategorizedGroupName = uncategorizedLabel,
                                onFavoriteClick = { channel -> LiveTvRepository.toggleFavoriteChannel(channel.id) },
                                onPlayClick = playChannel,
                                epgPrograms = uiState.epgPrograms,
                                nowMillis = epgNowMillis,
                                onGuideClick = { channel -> guideChannelId = channel.id },
                                onLongClick = { channel -> menuChannelId = channel.id },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(listTopPadding)
                    .background(screenBackground),
            )

            AnimatedVisibility(
                visible = showBackToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = liveTvBackToTopBottomPadding),
            ) {
                LiveTvBackToTopButton(
                    // TV: moving the focus up already scrolls back; as a target it only got in the way.
                    modifier = if (isTv) Modifier.tvNotFocusable() else Modifier,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                )
            }

            val menuChannel = menuChannelId?.let { channelId -> uiState.channels.firstOrNull { it.id == channelId } }
            if (menuChannel != null) {
                LiveTvChannelMenuSheet(
                    channel = menuChannel,
                    onDismiss = { menuChannelId = null },
                    onShowEpg = { guideChannelId = menuChannel.id },
                    onCopyLink = {
                        coroutineScope.launch {
                            // Portals (Stalker, Xtream) resolve the real stream link first.
                            val playable = runCatching { LiveTvRepository.prepareForPlayback(menuChannel) }
                                .onFailure { error ->
                                    if (error is CancellationException) throw error
                                    liveTvChannelMenuLog.w(error) { "Could not resolve the link of ${menuChannel.name}, copying the original" }
                                }
                                .getOrDefault(menuChannel)
                            clipboardManager.setText(AnnotatedString(playable.streamUrl))
                            liveTvChannelMenuLog.i { "Link copied: ${menuChannel.name}" }
                            NuvioToastController.show(linkCopiedText)
                        }
                    },
                    onOpenExternal = { onChannelExternalClick(menuChannel) },
                    onWatchPrivately = { onChannelPrivateClick(menuChannel) },
                )
            }

            val guideChannel = guideChannelId?.let { channelId -> uiState.channels.firstOrNull { it.id == channelId } }
            if (guideChannel != null) {
                LiveTvEpgGuideSheet(
                    channel = guideChannel,
                    programs = uiState.epgPrograms[guideChannel.id].orEmpty(),
                    nowMillis = epgNowMillis,
                    onPlayClick = {
                        guideChannelId = null
                        playChannel(guideChannel)
                    },
                    onDismiss = { guideChannelId = null },
                    onRecordClick = if (LiveTvRecordingsRepository.isSupported) {
                        {
                            guideChannelId = null
                            manualRecordingChannelId = guideChannel.id
                        }
                    } else {
                        null
                    },
                    onRecordProgram = if (LiveTvRecordingsRepository.isSupported) {
                        { program -> coroutineScope.launch { toggleLiveTvProgramRecording(guideChannel, program) } }
                    } else {
                        null
                    },
                    isProgramRecorded = { program ->
                        recordings.any {
                            it.channelId == guideChannel.id && it.title == program.title &&
                                (it.status == LiveTvRecordingStatus.Scheduled || it.status == LiveTvRecordingStatus.Recording) &&
                                it.endMillis >= program.endMillis
                        }
                    },
                )
            }

            val manualRecordingChannel = manualRecordingChannelId?.let { id -> uiState.channels.firstOrNull { it.id == id } }
            if (manualRecordingChannel != null) {
                LiveTvManualRecordingDialog(
                    channel = manualRecordingChannel,
                    onDismiss = { manualRecordingChannelId = null },
                )
            }

            val gameRecording = gameRecordingKey?.let { key -> sportsState.events.firstOrNull { liveTvSportsEventKey(it) == key } }
            if (gameRecording != null) {
                LiveTvGameRecordingDialog(
                    event = gameRecording,
                    channels = sportsMatches[liveTvSportsEventKey(gameRecording)].orEmpty(),
                    onDismiss = { gameRecordingKey = null },
                )
            }

            val seriesSheetItem = seriesSheetItemId?.let { id ->
                onDemandState.series.catalog.items.firstOrNull { it.id == id }
            }
            if (seriesSheetItem != null) {
                LiveTvSeriesSheet(
                    series = seriesSheetItem,
                    onEpisodeClick = { episode ->
                        onVodClick(LiveTvOnDemandRepository.episodePlayback(seriesSheetItem, episode))
                    },
                    onDismiss = { seriesSheetItemId = null },
                )
            }
        }
    }
}

/** Sticky container for the controls of the Guide, Sports and On Demand sections. */
@Composable
private fun LiveTvSectionControlsSurface(
    topPadding: Dp,
    background: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(top = topPadding + 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.nuvio.spacing.listGap),
    ) {
        content()
    }
}

private fun LazyListScope.liveTvLoadingItem() {
    item(key = "live-tv-loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.nuvio.colors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

private const val ChannelControlsKey = "channel-controls"

private val LiveTvScreenHorizontalPadding = 16.dp

@Composable
private fun LiveTvChannelControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categoryOptions: List<LiveTvCategoryFilterOption>,
    selectedCategoryName: String?,
    filterMode: LiveTvChannelFilterMode,
    allLabel: String,
    favoritesLabel: String,
    categoryLabel: String,
    channelCount: Int,
    background: Color,
    topPadding: Dp,
    onAllSelected: () -> Unit,
    onFavoritesSelected: () -> Unit,
    onGroupSelected: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(top = topPadding + 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
    ) {
        LiveTvSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveTvFilterPanelRow(
                modifier = Modifier.weight(1f),
                groups = categoryOptions
                    .filter { option -> option.mode == LiveTvChannelFilterMode.Category }
                    .map { option -> option.label },
                selectedGroup = selectedCategoryName,
                favoritesOnly = filterMode == LiveTvChannelFilterMode.Favorites,
                allLabel = allLabel,
                favoritesLabel = favoritesLabel,
                categoryLabel = categoryLabel,
                onAllSelected = onAllSelected,
                onFavoritesSelected = onFavoritesSelected,
                onGroupSelected = onGroupSelected,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = channelCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LiveTvBackToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier.size(48.dp),
        onClick = onClick,
        color = tokens.colors.overlaySelected,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(NuvioTokens.Border.thin, tokens.colors.borderSubtle),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.live_tv_action_back_to_top),
                tint = tokens.colors.accent,
            )
        }
    }
}

@Composable
private fun LiveTvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.live_tv_search_placeholder),
) {
    Box(modifier = modifier.fillMaxWidth()) {
        NuvioInputField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            trailingContent = {
                if (query.isBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.nuvio.colors.textMuted,
                    )
                } else {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.nuvio.colors.textMuted,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun LiveTvFilterRow(
    filterMode: LiveTvChannelFilterMode,
    selectedCategoryName: String?,
    allChannelsLabel: String,
    favoritesLabel: String,
    chooseCategoryLabel: String,
    categoryOptions: List<LiveTvCategoryFilterOption>,
    onAllChannelsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCategoryOptionClick: (LiveTvCategoryFilterOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvFilterChip(
            label = allChannelsLabel,
            selected = filterMode == LiveTvChannelFilterMode.All,
            onClick = onAllChannelsClick,
        )
        LiveTvFilterChip(
            label = favoritesLabel,
            selected = filterMode == LiveTvChannelFilterMode.Favorites,
            onClick = onFavoritesClick,
        )
        LiveTvCategoryFilterChip(
            selected = filterMode == LiveTvChannelFilterMode.Category,
            label = selectedCategoryName ?: chooseCategoryLabel,
            options = categoryOptions,
            onOptionClick = onCategoryOptionClick,
        )
    }
}

@Composable
private fun LiveTvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(if (selected) tokens.colors.overlaySelected else tokens.colors.surface)
            .then(
                if (selected) Modifier else Modifier.border(
                    width = NuvioTokens.Border.thin,
                    color = tokens.colors.borderSubtle,
                    shape = tokens.shapes.chip,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveTvCategoryFilterChip(
    selected: Boolean,
    label: String,
    options: List<LiveTvCategoryFilterOption>,
    onOptionClick: (LiveTvCategoryFilterOption) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    val shape = tokens.shapes.chip

    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (selected) tokens.colors.overlaySelected else tokens.colors.surface)
                .border(
                    width = if (selected) 0.dp else NuvioTokens.Border.thin,
                    color = if (selected) Color.Transparent else tokens.colors.borderSubtle,
                    shape = shape,
                )
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionClick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveTvRecentChannelCard(
    channel: LiveTvChannel,
    categoryName: String,
    onContinueClick: () -> Unit,
    programs: List<LiveTvEpgProgram> = emptyList(),
    nowMillis: Long = 0L,
    onGuideClick: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onContinueClick,
        color = tokens.colors.surface,
        shape = tokens.shapes.card,
        border = BorderStroke(NuvioTokens.Border.thin, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel = channel,
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(16.dp),
                iconTint = tokens.colors.accent,
                borderColor = tokens.colors.borderSubtle,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_last_watched_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvCategoryLine(categoryName = categoryName)
                if (programs.isNotEmpty()) {
                    LiveTvEpgNowNext(
                        programs = programs,
                        nowMillis = nowMillis,
                        onClick = onGuideClick,
                    )
                }
            }

            Surface(
                color = tokens.colors.overlaySelected,
                shape = tokens.shapes.chip,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                    )
                    Text(
                        text = stringResource(Res.string.live_tv_button_continue),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.liveTvChannelList(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    uncategorizedGroupName: String,
    onFavoriteClick: (LiveTvChannel) -> Unit,
    onPlayClick: (LiveTvChannel) -> Unit,
    epgPrograms: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
    nowMillis: Long = 0L,
    onGuideClick: (LiveTvChannel) -> Unit = {},
    onLongClick: (LiveTvChannel) -> Unit = {},
) {
    items(
        items = channels,
        key = { channel -> "channel:${channel.id}" },
    ) { channel ->
        LiveTvChannelCard(
            channel = channel,
            categoryName = categoryNameForChannel(channel, uncategorizedGroupName),
            isFavorite = channel.id in favoriteChannelIds,
            onFavoriteClick = { onFavoriteClick(channel) },
            onPlayClick = { onPlayClick(channel) },
            programs = epgPrograms[channel.id].orEmpty(),
            nowMillis = nowMillis,
            onGuideClick = { onGuideClick(channel) },
            onLongClick = { onLongClick(channel) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvChannelCard(
    channel: LiveTvChannel,
    categoryName: String,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    programs: List<LiveTvEpgProgram> = emptyList(),
    nowMillis: Long = 0L,
    onGuideClick: () -> Unit = {},
    /** Channel menu: long press (touch), OK held or Menu (TV), right click (desktop). */
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .then(if (onLongClick != null) Modifier.liveTvSecondaryClick(onLongClick) else Modifier)
            .tvLongPressKeys(onClick = onPlayClick, onLongClick = onLongClick)
            .combinedClickable(onClick = onPlayClick, onLongClick = onLongClick),
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChannelLogo(channel = channel)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvCategoryLine(categoryName = categoryName)
                if (programs.isNotEmpty()) {
                    LiveTvEpgNowNext(
                        programs = programs,
                        nowMillis = nowMillis,
                        onClick = onGuideClick,
                    )
                }
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) tokens.colors.warning else tokens.colors.textMuted,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(tokens.shapes.avatar)
                    .background(tokens.colors.overlaySelected)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun LiveTvCategoryLine(categoryName: String) {
    val tokens = MaterialTheme.nuvio
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(tokens.colors.accent),
        )
        Text(
            text = categoryName,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelLogo(
    channel: LiveTvChannel,
    modifier: Modifier = Modifier.size(64.dp),
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    iconTint: Color = MaterialTheme.nuvio.colors.textMuted,
    borderColor: Color? = null,
) {
    val tokens = MaterialTheme.nuvio
    val boxModifier = modifier
        .clip(shape)
        .background(tokens.colors.surfaceCard)
        .then(
            if (borderColor != null) {
                Modifier.border(
                    width = NuvioTokens.Border.thin,
                    color = borderColor,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private enum class LiveTvChannelFilterMode {
    All,
    Favorites,
    Category,
}

private data class LiveTvCategoryFilterOption(
    val label: String,
    val mode: LiveTvChannelFilterMode,
    val categoryName: String? = null,
)

private fun buildLiveTvCategoryFilterOptions(
    channels: List<LiveTvChannel>,
    allChannelsLabel: String,
    favoritesLabel: String,
    uncategorizedLabel: String,
): List<LiveTvCategoryFilterOption> {
    val categories = channels
        .mapNotNull { channel -> channel.group?.trim()?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
        .filterNot { category ->
            category.equals(allChannelsLabel, ignoreCase = true) ||
                category.equals(favoritesLabel, ignoreCase = true) ||
                category.equals(uncategorizedLabel, ignoreCase = true)
        }
        .sortedBy { it.lowercase() }

    return buildList {
        add(LiveTvCategoryFilterOption(allChannelsLabel, LiveTvChannelFilterMode.All))
        add(LiveTvCategoryFilterOption(favoritesLabel, LiveTvChannelFilterMode.Favorites))
        add(LiveTvCategoryFilterOption(uncategorizedLabel, LiveTvChannelFilterMode.Category, uncategorizedLabel))
        categories.forEach { category ->
            add(LiveTvCategoryFilterOption(category, LiveTvChannelFilterMode.Category, category))
        }
    }
}

private fun filterLiveTvChannels(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    filterMode: LiveTvChannelFilterMode,
    selectedCategoryName: String?,
    searchQuery: String,
    uncategorizedGroupName: String,
): List<LiveTvChannel> {
    val normalizedQuery = searchQuery.trim().lowercase()
    return channels
        .asSequence()
        .filter { channel ->
            when (filterMode) {
                LiveTvChannelFilterMode.All -> true
                LiveTvChannelFilterMode.Favorites -> channel.id in favoriteChannelIds
                LiveTvChannelFilterMode.Category -> selectedCategoryName == null ||
                    categoryNameForChannel(channel, uncategorizedGroupName).equals(selectedCategoryName, ignoreCase = true)
            }
        }
        .filter { channel ->
            normalizedQuery.isBlank() ||
                channel.name.lowercase().contains(normalizedQuery) ||
                categoryNameForChannel(channel, uncategorizedGroupName).lowercase().contains(normalizedQuery) ||
                channel.playlistName.orEmpty().lowercase().contains(normalizedQuery)
        }
        // Already in the user's order (sorted by name unless reordered in the settings).
        .toList()
}

private fun categoryNameForChannel(
    channel: LiveTvChannel,
    uncategorizedGroupName: String,
): String = channel.group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedGroupName
