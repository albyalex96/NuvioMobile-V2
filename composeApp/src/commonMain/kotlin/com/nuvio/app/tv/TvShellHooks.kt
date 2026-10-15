package com.nuvio.app.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.AppScreenTab
import com.nuvio.app.AppTabActions
import com.nuvio.app.AppTabHost
import com.nuvio.app.AppTabRequests
import com.nuvio.app.AppTabState
import com.nuvio.app.MainTabsDestination
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.groupedEpisodesForDisplay
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.canOpenCatalog
import com.nuvio.app.features.player.PlayerScreenRuntime
import com.nuvio.app.features.player.PlayerSeekDirection
import com.nuvio.app.features.player.handleDoubleTapSeek
import com.nuvio.app.features.player.playNextEpisode
import com.nuvio.app.features.player.scheduleProgressSyncAfterSeek
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.player.togglePlayback
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem

private val log = Logger.withTag("TvShell")

/** Number of catalog items shown in a TV home row before "see all". */
private const val TV_HOME_ROW_LIMIT = 24

@Composable
internal fun TvShellThemeOverlay(content: @Composable () -> Unit) {
    val shell = LocalTvShell.current
    if (shell == null) {
        content()
    } else {
        shell.themeOverlay(content)
    }
}

/**
 * Same contract as [MainTabsDestination]. With a [TvShell] the mobile navigation bars are
 * replaced by the TV chrome (sidebar) while the tab content stays the shared [AppTabHost].
 */
@Composable
internal fun ShellTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    showLiveTvInNavigation: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    val shell = LocalTvShell.current
    if (shell == null) {
        MainTabsDestination(
            selectedTab = selectedTab,
            initialHomeReady = initialHomeReady,
            rootRouteActive = rootRouteActive,
            useTabletFloatingTabBar = useTabletFloatingTabBar,
            useNativeNavigation = useNativeNavigation,
            useNativeTabBar = useNativeTabBar,
            liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
            liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
            showLiveTvInNavigation = showLiveTvInNavigation,
            requests = requests,
            state = state,
            actions = actions,
            onBack = onBack,
            onTabSelected = onTabSelected,
            onProfileSelected = onProfileSelected,
            onAddProfileRequested = onAddProfileRequested,
        )
        return
    }

    PlatformBackHandler(enabled = rootRouteActive, onBack = onBack)
    val tabActions = remember(actions) { actions(true) }
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val tabs = remember(showLiveTvInNavigation) {
        buildList {
            add(AppScreenTab.Home)
            add(AppScreenTab.Search)
            add(AppScreenTab.Library)
            if (showLiveTvInNavigation) add(AppScreenTab.LiveTv)
            add(AppScreenTab.Settings)
        }
    }
    val chrome = TvTabsChrome(
        selectedTab = selectedTab,
        tabs = tabs,
        activeProfile = profileState.activeProfile,
        contentVisible = initialHomeReady,
        onTabSelected = onTabSelected,
        onSwitchProfile = onAddProfileRequested,
    )
    shell.tabsChrome(chrome) {
        CompositionLocalProvider(
            LocalNuvioBottomNavigationOverlayPadding provides 0.dp,
            LocalNuvioNavBarScrollState provides rememberNuvioNavBarScrollState(),
        ) {
            Box(modifier = Modifier.fillMaxSize().alpha(if (initialHomeReady) 1f else 0f)) {
                AppTabHost(
                    selectedTab = selectedTab,
                    requests = requests,
                    state = state,
                    actions = tabActions,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

internal fun buildTvHomeModel(
    homeUiState: HomeUiState,
    enabledHomeItems: List<HomeCatalogSettingsItem>,
    sectionsMap: Map<String, HomeCatalogSection>,
    collectionsMap: Map<String, Collection>,
    continueWatchingItems: List<ContinueWatchingItem>,
    upcomingItems: List<ContinueWatchingItem>,
    showContinueWatching: Boolean,
    isInitialLoading: Boolean,
    hasContentSources: Boolean,
    addonManifestErrorMessage: String?,
    isPosterWatched: (MetaPreview) -> Boolean,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)?,
    onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    onRetry: () -> Unit,
): TvHomeModel {
    val rows = enabledHomeItems.mapNotNull { item ->
        if (item.isCollection) {
            collectionsMap[item.key]?.let { collection -> TvHomeRow.Folders(key = item.key, collection = collection) }
        } else {
            sectionsMap[item.key]
                ?.takeIf { it.items.isNotEmpty() }
                ?.let { section ->
                    TvHomeRow.Catalog(
                        key = item.key,
                        section = section,
                        items = section.items.take(TV_HOME_ROW_LIMIT),
                        canOpenCatalog = section.canOpenCatalog(TV_HOME_ROW_LIMIT),
                    )
                }
        }
    }.distinctBy(TvHomeRow::key)
    val visibleContinueWatching = if (showContinueWatching) continueWatchingItems else emptyList()
    val visibleUpcoming = if (showContinueWatching) upcomingItems else emptyList()
    val status = when {
        isInitialLoading -> TvHomeStatus.Loading
        !hasContentSources && rows.isEmpty() -> TvHomeStatus.NoSources
        rows.isEmpty() && homeUiState.heroItems.isEmpty() &&
            visibleContinueWatching.isEmpty() && visibleUpcoming.isEmpty() -> {
            if (homeUiState.errorMessage.isNullOrBlank() && addonManifestErrorMessage == null) {
                TvHomeStatus.Empty
            } else {
                TvHomeStatus.Error
            }
        }
        else -> TvHomeStatus.Content
    }
    return TvHomeModel(
        status = status,
        errorMessage = homeUiState.errorMessage ?: addonManifestErrorMessage,
        heroItems = homeUiState.heroItems,
        continueWatching = visibleContinueWatching,
        upcoming = visibleUpcoming,
        rows = rows,
        isPosterWatched = isPosterWatched,
        onPosterClick = onPosterClick ?: {},
        onPosterLongClick = onPosterLongClick,
        onContinueWatchingClick = onContinueWatchingClick ?: {},
        onContinueWatchingLongClick = onContinueWatchingLongPress,
        onCatalogClick = onCatalogClick,
        onFolderClick = onFolderClick,
        onRetry = onRetry,
    )
}

internal fun buildTvDetailsModel(
    meta: MetaDetails,
    hasEpisodes: Boolean,
    primaryActionLabel: String,
    primaryActionEnabled: Boolean,
    primaryActionSeason: Int?,
    primaryActionEpisode: Int?,
    primaryActionProgress: Float?,
    onPrimaryAction: () -> Unit,
    onPrimaryActionManual: (() -> Unit)?,
    episodeProgress: (MetaVideo) -> Float?,
    isEpisodeWatched: (MetaVideo) -> Boolean,
    onEpisodeClick: (MetaVideo) -> Unit,
    onEpisodeManualClick: ((MetaVideo) -> Unit)?,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    shuffleActive: Boolean,
    onShuffleClick: (() -> Unit)?,
    onOpenMeta: ((MetaPreview) -> Unit)?,
    onBack: () -> Unit,
): TvDetailsModel {
    val isSeries = meta.type == "series" || hasEpisodes
    val seasons = if (hasEpisodes) {
        meta.groupedEpisodesForDisplay()
            .entries
            .sortedBy { (season, _) -> if (season <= 0) Int.MAX_VALUE else season }
            .map { (season, episodes) -> TvSeason(number = season, episodes = episodes) }
    } else {
        emptyList()
    }
    return TvDetailsModel(
        meta = meta,
        isSeries = isSeries,
        primaryActionLabel = primaryActionLabel,
        primaryActionEnabled = primaryActionEnabled,
        primaryActionSeason = primaryActionSeason,
        primaryActionEpisode = primaryActionEpisode,
        primaryActionProgress = primaryActionProgress,
        onPrimaryAction = onPrimaryAction,
        onPrimaryActionManual = onPrimaryActionManual,
        seasons = seasons,
        episodeProgress = episodeProgress,
        isEpisodeWatched = isEpisodeWatched,
        onEpisodeClick = onEpisodeClick,
        onEpisodeManualClick = onEpisodeManualClick,
        isSaved = isSaved,
        onToggleSaved = onToggleSaved,
        isWatched = isWatched,
        onToggleWatched = onToggleWatched,
        shuffleActive = shuffleActive,
        onShuffleClick = onShuffleClick,
        onOpenMeta = onOpenMeta ?: {},
        onBack = onBack,
    )
}

/** Exposes the player runtime to the TV remote while the player is on screen (TV shell only). */
@Composable
internal fun PlayerScreenRuntime.BindTvPlayerRemote() {
    if (LocalTvShell.current == null) return
    val runtime by rememberUpdatedState(this)
    DisposableEffect(Unit) {
        val remote = object : TvPlayerRemote {
            override val controlsVisible: Boolean
                get() = runtime.controlsVisible

            override val isPlaying: Boolean
                get() = runtime.playbackSnapshot.isPlaying

            override val overlayOpen: Boolean
                get() = with(runtime) {
                    showSourcesPanel || showEpisodesPanel || showLiveChannelsPanel ||
                        showSubmitIntroModal || showAudioModal || showSubtitleModal ||
                        showVideoSettingsModal || showStillWatchingPrompt ||
                        pendingP2pSwitch != null || errorMessage != null
                }

            override fun togglePlayback() {
                runtime.togglePlayback()
            }

            override fun activatePrompt(): Boolean {
                val interval = runtime.activeSkipInterval
                if (interval != null && !runtime.skipIntervalDismissed) {
                    val durationMs = runtime.playbackSnapshot.durationMs
                    val action = interval.internalSkipAction(runtime.skipIntervals, durationMs)
                    if (action != null) {
                        val seekMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                        runtime.playerController?.seekTo(seekMs)
                        runtime.scheduleProgressSyncAfterSeek()
                        runtime.skipIntervalDismissed = true
                        log.d { "Remote skipped interval to ${seekMs}ms" }
                        return true
                    }
                }
                if (runtime.showNextEpisodeCard && runtime.nextEpisodeInfo != null) {
                    log.d { "Remote started next episode from prompt" }
                    runtime.playNextEpisode()
                    return true
                }
                return false
            }

            override fun seekStep(forward: Boolean) {
                runtime.handleDoubleTapSeek(
                    if (forward) PlayerSeekDirection.Forward else PlayerSeekDirection.Backward,
                )
            }

            override fun showControls() {
                runtime.controlsVisible = true
                runtime.controlsActivityTick += 1
            }

            override fun hideControls() {
                runtime.controlsVisible = false
            }

            override fun registerActivity() {
                runtime.controlsActivityTick += 1
            }
        }
        TvPlayerRemoteRegistry.active = remote
        log.i { "Player remote bound" }
        onDispose {
            if (TvPlayerRemoteRegistry.active === remote) {
                TvPlayerRemoteRegistry.active = null
            }
            log.i { "Player remote released" }
        }
    }
}
