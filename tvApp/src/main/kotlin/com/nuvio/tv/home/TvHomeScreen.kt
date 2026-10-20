package com.nuvio.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.localizedContinueWatchingSubtitle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.tv.TvHomeModel
import com.nuvio.app.tv.TvHomeRow
import com.nuvio.app.tv.TvHomeStatus
import com.nuvio.tv.R
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.tv.settings.TvLayoutSettings
import com.nuvio.tv.launcher.TvWatchNextSync
import com.nuvio.tv.settings.rememberTvLayoutSettings
import com.nuvio.tv.ui.TvActionButton
import com.nuvio.tv.ui.TvCardMetrics
import com.nuvio.tv.ui.TvLandscapeArtwork
import com.nuvio.tv.ui.TvRowCardFocusBorder
import com.nuvio.tv.ui.TvRowLandscapeCard
import com.nuvio.tv.ui.TvRowPosterCard
import com.nuvio.tv.ui.cardMetrics
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvMessage
import com.nuvio.tv.ui.landscapeArtwork
import com.nuvio.tv.ui.showsLandscape
import com.nuvio.tv.ui.tvFocusable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

private val log = Logger.withTag("TvHome")

/** Delay before the hero follows the focused card, so fast scrolling does not flicker. */
private const val HERO_FOLLOW_DELAY_MS = 220L

/** NuvioTV `HeroCarousel`: slides advance by themselves every 10 s. */
private const val HERO_AUTO_ADVANCE_MS = 10_000L
private const val HERO_FOCUS_KEY = "hero"
/** The carousel dots sit low, partly in the gap above the first row, to leave room to the plot. */
private val HERO_DOTS_OFFSET = 8.dp
/** Space kept free under the hero text for the carousel dots. */
private val HERO_DOTS_BAND = 12.dp
private val HERO_ROWS_GAP = 14.dp

/** Collapse/expand of the hero when the first row leaves or re-enters the screen. */
private const val HERO_COLLAPSE_ANIMATION_MS = 350

/** NuvioTV never expands a focused poster sooner than this, even with a 0 s delay. */
private const val MIN_EXPAND_DELAY_MS = 150L

// Hero text block of the NuvioTV modern home.
private const val HERO_TEXT_WIDTH_FRACTION = 0.5f
private val HERO_LOGO_HEIGHT = 100.dp
private val HERO_LOGO_MAX_WIDTH = 220.dp

private class HeroInfo(
    val key: String,
    val title: String,
    val logo: String?,
    val backdrop: String?,
    val metaLine: String?,
    val description: String?,
)

@Composable
fun TvHomeScreen(model: TvHomeModel) {
    TvWatchNextSync(items = model.continueWatching, ready = model.status == TvHomeStatus.Content)
    when (model.status) {
        TvHomeStatus.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.nuvio.colors.accent)
                Text(
                    text = stringResource(R.string.tv_home_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
        TvHomeStatus.NoSources -> CenteredMessage(
            title = stringResource(R.string.tv_home_no_sources_title),
            message = stringResource(R.string.tv_home_no_sources_message),
            onRetry = null,
        )
        TvHomeStatus.Empty -> CenteredMessage(
            title = stringResource(R.string.tv_home_empty_title),
            message = stringResource(R.string.tv_home_empty_message),
            onRetry = null,
        )
        TvHomeStatus.Error -> CenteredMessage(
            title = stringResource(R.string.tv_home_error_title),
            message = model.errorMessage,
            onRetry = model.onRetry,
        )
        TvHomeStatus.Content -> TvHomeContent(model)
    }
}

@Composable
private fun CenteredMessage(title: String, message: String?, onRetry: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvMessage(
            title = title,
            message = message,
            action = onRetry?.let { retry ->
                {
                    TvActionButton(
                        label = stringResource(R.string.tv_action_retry),
                        icon = Icons.Filled.Refresh,
                        primary = true,
                        onClick = retry,
                    )
                }
            },
        )
    }
}

@Composable
private fun TvHomeContent(model: TvHomeModel) {
    val settings = rememberTvLayoutSettings()
    val metrics = remember(settings) { settings.cardMetrics() }
    val continueWatchingPrefs by ContinueWatchingPreferencesRepository.uiState.collectAsState()
    LaunchedEffect(Unit) { ContinueWatchingPreferencesRepository.ensureLoaded() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Card focused in a catalog row, and the one widened to its backdrop after the delay.
    var focusedPosterKey by remember { mutableStateOf<String?>(null) }
    var expandedPosterKey by remember { mutableStateOf<String?>(null) }
    val rowsState = rememberLazyListState()
    LaunchedEffect(focusedPosterKey, settings.expandFocusedPoster, settings.expandDelaySeconds, rowsState.isScrollInProgress) {
        // Always collapse first: moving the focus or scrolling shrinks the expanded card.
        expandedPosterKey = null
        val key = focusedPosterKey ?: return@LaunchedEffect
        if (!settings.expandFocusedPoster || rowsState.isScrollInProgress) return@LaunchedEffect
        delay((settings.expandDelaySeconds * 1_000L).coerceAtLeast(MIN_EXPAND_DELAY_MS))
        expandedPosterKey = key
        log.d { "Focused poster expanded to its backdrop" }
    }

    val defaultHero = remember(model.heroItems, model.continueWatching) {
        model.continueWatching.firstOrNull()?.let { heroOf(it, subtitle = null) }
            ?: model.heroItems.firstOrNull()?.let(::heroOf)
    }
    var focusedHero by remember { mutableStateOf<HeroInfo?>(null) }
    var displayedHero by remember { mutableStateOf(defaultHero) }
    LaunchedEffect(focusedHero?.key) {
        val next = focusedHero ?: return@LaunchedEffect
        delay(HERO_FOLLOW_DELAY_MS)
        displayedHero = next
    }
    if (displayedHero == null && defaultHero != null) displayedHero = defaultHero

    // Hero carousel of NuvioTV (hero sources of Settings -> Home screen): shown while the hero has
    // the focus or no card was focused yet; a focused card replaces it with its own preview.
    val carouselItems = model.heroItems
    val heroRequester = requesterFor(HERO_FOCUS_KEY)
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeHeroIndex = heroIndex.coerceIn(0, (carouselItems.size - 1).coerceAtLeast(0))
    var heroFocused by remember { mutableStateOf(false) }
    val carouselActive = carouselItems.isNotEmpty() && focusedHero == null
    val carouselHero = carouselItems.getOrNull(activeHeroIndex)?.takeIf { carouselActive }
    LaunchedEffect(carouselActive, activeHeroIndex, carouselItems.size) {
        if (!carouselActive || carouselItems.size <= 1) return@LaunchedEffect
        // A manual change restarts the wait (the effect is keyed on the index).
        delay(HERO_AUTO_ADVANCE_MS)
        heroIndex = (activeHeroIndex + 1) % carouselItems.size
    }

    val firstKey = remember(model) { firstFocusableKey(model) }
    LaunchedEffect(firstKey) {
        // NuvioTV starts on the hero carousel when there is one.
        val initialKey = lastFocusedKey ?: if (carouselItems.isNotEmpty()) HERO_FOCUS_KEY else firstKey
        val target = initialKey?.let(requesters::get) ?: firstKey?.let(requesters::get) ?: return@LaunchedEffect
        // The lazy rows compose their first items a few frames after the list.
        repeat(10) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not move the initial focus into the home rows" }
    }

    fun onCardFocused(key: String, hero: HeroInfo) {
        // Leaving the carousel: the card preview replaces it at once, without the follow delay.
        if (focusedHero == null) displayedHero = hero
        lastFocusedKey = key
        focusedHero = hero
        focusedPosterKey = null
    }

    fun onPosterFocused(key: String, hero: HeroInfo) {
        onCardFocused(key, hero)
        focusedPosterKey = key
    }

    // The hero belongs to the top of the page: once the first row scrolls off the screen it
    // collapses (backdrop included) and the rows take the whole height.
    val heroHidden by remember { derivedStateOf { rowsState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(heroHidden) { log.d { if (heroHidden) "Hero collapsed" else "Hero expanded" } }
    val heroProgress by animateFloatAsState(
        targetValue = if (heroHidden) 0f else 1f,
        animationSpec = tween(HERO_COLLAPSE_ANIMATION_MS),
        label = "tvHomeHeroCollapse",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // NuvioTV keeps the rows in the bottom 52% of the screen (49% with landscape posters).
        val heroHeight = maxHeight * (1f - if (settings.landscapePosters) 0.49f else 0.52f)
        val shownHero = carouselHero?.let(::heroOf) ?: displayedHero
        if (heroProgress > 0f) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = heroProgress }) {
                HeroBackdrop(url = shownHero?.backdrop, fullScreen = settings.heroFullScreenBackdrop)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight * heroProgress)
                    // Clipped only while collapsing: the dots hang a little below the hero.
                    .then(if (heroProgress < 1f) Modifier.clipToBounds() else Modifier)
                    .graphicsLayer { alpha = heroProgress },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Full height while collapsing: the hero slides up instead of being squashed.
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                        .height(heroHeight),
                ) {
                    // The carousel dots get their own band below the hero text.
                    HeroInfoPanel(
                        hero = shownHero,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (carouselItems.isNotEmpty()) HERO_DOTS_BAND else 0.dp),
                    )
                    if (carouselItems.isNotEmpty()) {
                        HeroCarouselFocusTarget(
                            count = carouselItems.size,
                            activeIndex = activeHeroIndex,
                            visible = carouselActive,
                            focused = heroFocused,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = !heroHidden }
                                .focusRequester(heroRequester)
                                .onFocusChanged { state ->
                                    heroFocused = state.isFocused
                                    if (state.isFocused) {
                                        lastFocusedKey = HERO_FOCUS_KEY
                                        focusedHero = null
                                        focusedPosterKey = null
                                    }
                                },
                            onPrevious = { (activeHeroIndex > 0).also { moved -> if (moved) heroIndex = activeHeroIndex - 1 } },
                            onNext = { (activeHeroIndex < carouselItems.lastIndex).also { moved -> if (moved) heroIndex = activeHeroIndex + 1 } },
                            onClick = {
                                carouselItems.getOrNull(activeHeroIndex)?.let { item ->
                                    log.d { "Hero carousel item opened" }
                                    model.onPosterClick(item)
                                }
                            },
                        )
                    }
                }
            }
            val continueWatchingSubtitle: @Composable (ContinueWatchingItem) -> String = { item ->
                localizedContinueWatchingSubtitle(item, compact = true)
            }
            LazyColumn(
                state = rowsState,
                // Room between the carousel dots and the first row.
                // Collapsed hero: the rows start below the floating pill / top bar.
                contentPadding = PaddingValues(
                    top = (if (carouselItems.isNotEmpty()) HERO_ROWS_GAP else 0.dp) * heroProgress +
                        TvDimens.chromeTopInset * (1f - heroProgress),
                    bottom = 48.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(TvDimens.rowGap - TvDimens.rowVerticalPadding * 2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Focus leaving the rows (sidebar, top bar) collapses the expanded poster.
                    .onFocusChanged { state -> if (!state.hasFocus) focusedPosterKey = null }
                    .focusRestorer(),
            ) {
                if (model.continueWatching.isNotEmpty()) {
                    item(key = "continue_watching", contentType = "landscape_row") {
                        ContinueWatchingRow(
                            title = stringResource(R.string.tv_home_continue_watching),
                            items = model.continueWatching,
                            keyPrefix = "cw",
                            upcoming = false,
                            model = model,
                            settings = settings,
                            metrics = metrics,
                            useEpisodeThumbnails = continueWatchingPrefs.useEpisodeThumbnails,
                            blurNextUp = continueWatchingPrefs.blurNextUp,
                            subtitle = continueWatchingSubtitle,
                            requesterFor = ::requesterFor,
                            onFocused = ::onCardFocused,
                        )
                    }
                }
                if (model.upcoming.isNotEmpty()) {
                    item(key = "upcoming", contentType = "landscape_row") {
                        ContinueWatchingRow(
                            title = stringResource(R.string.tv_home_upcoming),
                            items = model.upcoming,
                            keyPrefix = "up",
                            upcoming = true,
                            model = model,
                            settings = settings,
                            metrics = metrics,
                            useEpisodeThumbnails = continueWatchingPrefs.useEpisodeThumbnails,
                            blurNextUp = continueWatchingPrefs.blurNextUp,
                            subtitle = continueWatchingSubtitle,
                            requesterFor = ::requesterFor,
                            onFocused = ::onCardFocused,
                        )
                    }
                }
                items(model.rows, key = TvHomeRow::key, contentType = { row -> row::class.simpleName }) { row ->
                    when (row) {
                        is TvHomeRow.Catalog -> CatalogRow(
                            row = row,
                            model = model,
                            settings = settings,
                            metrics = metrics,
                            expandedKey = expandedPosterKey,
                            requesterFor = ::requesterFor,
                            onFocused = ::onCardFocused,
                            onPosterFocused = ::onPosterFocused,
                        )
                        is TvHomeRow.Folders -> FoldersRow(
                            row = row,
                            model = model,
                            settings = settings,
                            metrics = metrics,
                            requesterFor = ::requesterFor,
                            onFocused = { focusedPosterKey = null },
                        )
                    }
                }
            }
        }
    }
}

private val ConfirmKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Focus target of the hero carousel (NuvioTV `HeroCarousel`): left/right change the slide, OK opens
 * the details, the indicator dots sit at the bottom. Left on the first slide is not consumed, so it
 * still opens the sidebar.
 */
@Composable
private fun HeroCarouselFocusTarget(
    count: Int,
    activeIndex: Int,
    visible: Boolean,
    focused: Boolean,
    onPrevious: () -> Boolean,
    onNext: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> if (rtl) onNext() else onPrevious()
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> if (rtl) onPrevious() else onNext()
                    event.key in ConfirmKeys && event.type == KeyEventType.KeyDown -> true
                    event.key in ConfirmKeys && event.type == KeyEventType.KeyUp -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        if (visible && count > 1) {
            val accent = MaterialTheme.nuvio.colors.accent
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = HERO_DOTS_OFFSET),
            ) {
                repeat(count) { index ->
                    val active = index == activeIndex
                    Box(
                        modifier = Modifier
                            .size(width = if (active) 32.dp else 12.dp, height = if (active) 6.dp else 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    active && focused -> accent
                                    active -> Color.White.copy(alpha = 0.85f)
                                    else -> Color.White.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(url: String?, fullScreen: Boolean) {
    val background = MaterialTheme.nuvio.colors.background
    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = url,
            animationSpec = tween(450),
            label = "tvHomeBackdrop",
            // "Fullscreen hero backdrop" (NuvioTV): the artwork fills the whole screen.
            modifier = if (fullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.72f)
            },
        ) { backdrop ->
            if (!backdrop.isNullOrBlank()) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background,
                        0.32f to background.copy(alpha = 0.92f),
                        0.6f to background.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to background.copy(alpha = 0.35f),
                        0.68f to background,
                    ),
                ),
        )
    }
}

@Composable
private fun HeroInfoPanel(hero: HeroInfo?, modifier: Modifier = Modifier) {
    Crossfade(targetState = hero, animationSpec = tween(300), label = "tvHomeHeroInfo", modifier = modifier) { info ->
        if (info == null) return@Crossfade
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(HERO_TEXT_WIDTH_FRACTION)
                .padding(start = TvDimens.rowPadding, top = TvDimens.chromeTopInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!info.logo.isNullOrBlank()) {
                AsyncImage(
                    model = info.logo,
                    contentDescription = info.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .height(HERO_LOGO_HEIGHT)
                        .widthIn(max = HERO_LOGO_MAX_WIDTH),
                )
            } else {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!info.metaLine.isNullOrBlank()) {
                Text(
                    text = info.metaLine,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!info.description.isNullOrBlank()) {
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<ContinueWatchingItem>,
    keyPrefix: String,
    upcoming: Boolean,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    useEpisodeThumbnails: Boolean,
    blurNextUp: Boolean,
    subtitle: @Composable (ContinueWatchingItem) -> String,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    TvContentRow(title = title) {
        items(items, key = { item -> "$keyPrefix:${continueWatchingKey(item)}" }) { item ->
            val key = "$keyPrefix:${continueWatchingKey(item)}"
            val itemSubtitle = subtitle(item)
            TvContinueWatchingCard(
                item = item,
                style = settings.continueWatchingStyle,
                metrics = metrics,
                useEpisodeThumbnails = useEpisodeThumbnails,
                blurNextUp = blurNextUp,
                upcoming = upcoming,
                onClick = { model.onContinueWatchingClick(item) },
                onLongClick = model.onContinueWatchingLongClick?.let { longClick -> { longClick(item) } },
                onFocused = { onFocused(key, heroOf(item, itemSubtitle)) },
                modifier = Modifier.focusRequester(requesterFor(key)),
            )
        }
    }
}

@Composable
private fun CatalogRow(
    row: TvHomeRow.Catalog,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    expandedKey: String?,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
    onPosterFocused: (String, HeroInfo) -> Unit,
) {
    val landscape = settings.showsLandscape(row.items)
    TvContentRow(title = row.section.title) {
        items(row.items, key = { item -> "${row.key}:${item.stableKey()}" }) { item ->
            val key = "${row.key}:${item.stableKey()}"
            val focusModifier = Modifier.focusRequester(requesterFor(key))
            if (landscape) {
                TvRowLandscapeCard(
                    artwork = settings.landscapeArtwork(item),
                    title = item.name,
                    width = metrics.landscapeWidth,
                    height = metrics.landscapeHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = model.isPosterWatched(item),
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            } else {
                val square = item.posterShape == PosterShape.Square
                // Only portrait posters with a real backdrop expand (NuvioTV: catalog items only).
                val expandedArtwork = if (square) null else settings.landscapeArtwork(item).takeUnless { it.fitImage }
                TvRowPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    width = if (square) metrics.squareSize else metrics.posterWidth,
                    height = if (square) metrics.squareSize else metrics.posterHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = model.isPosterWatched(item),
                    expandedWidth = metrics.expandedPosterWidth,
                    expanded = expandedKey == key,
                    expandedArtwork = expandedArtwork,
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onPosterFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            }
        }
        val onCatalogClick = model.onCatalogClick
        if (row.canOpenCatalog && onCatalogClick != null) {
            item(key = "${row.key}:see_all") {
                SeeAllTile(
                    width = if (landscape) metrics.landscapeWidth else metrics.posterWidth,
                    height = if (landscape) metrics.landscapeHeight else metrics.posterHeight,
                    shape = metrics.shape,
                    onClick = { onCatalogClick(row.section) },
                )
            }
        }
    }
}

@Composable
private fun FoldersRow(
    row: TvHomeRow.Folders,
    model: TvHomeModel,
    settings: TvLayoutSettings,
    metrics: TvCardMetrics,
    requesterFor: (String) -> FocusRequester,
    onFocused: () -> Unit,
) {
    val onFolderClick = model.onFolderClick ?: return
    // The folder's own tile shape (collection editor) wins over the global landscape posters setting.
    TvContentRow(title = row.collection.title) {
        items(row.collection.folders, key = { folder -> "${row.key}:${folder.id}" }) { folder ->
            val key = "${row.key}:${folder.id}"
            val modifier = Modifier.focusRequester(requesterFor(key))
            val square = folder.tileShape.equals("square", ignoreCase = true)
            if (folder.isPosterTile() || square) {
                TvRowPosterCard(
                    imageUrl = folder.coverImageUrl,
                    title = folder.title,
                    width = if (square) metrics.squareSize else metrics.posterWidth,
                    height = if (square) metrics.squareSize else metrics.posterHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    onFocused = onFocused,
                    modifier = modifier,
                )
            } else {
                TvRowLandscapeCard(
                    artwork = TvLandscapeArtwork(imageUrl = folder.coverImageUrl, logoUrl = null, customLandscape = true),
                    title = folder.title,
                    width = metrics.landscapeWidth,
                    height = metrics.landscapeHeight,
                    shape = metrics.shape,
                    showLabel = settings.posterLabels,
                    watched = false,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    onFocused = onFocused,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SeeAllTile(width: Dp, height: Dp, shape: Shape, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .tvFocusable(
                shape = shape,
                onClick = onClick,
                focusedScale = 1f,
                borderWidth = TvRowCardFocusBorder,
                onFocusChange = { focused = it },
            )
            .background(if (focused) Color.White.copy(alpha = 0.18f) else MaterialTheme.nuvio.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.nuvio.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.tv_home_see_all),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
        }
    }
    Spacer(modifier = Modifier.width(TvDimens.screenPadding))
}

private fun CollectionFolder.isPosterTile(): Boolean =
    tileShape.isBlank() || tileShape.equals("poster", ignoreCase = true)

private fun continueWatchingKey(item: ContinueWatchingItem): String =
    "${item.parentMetaId}:${item.seasonNumber ?: -1}:${item.episodeNumber ?: -1}"

private fun firstFocusableKey(model: TvHomeModel): String? {
    model.continueWatching.firstOrNull()?.let { return "cw:${continueWatchingKey(it)}" }
    model.upcoming.firstOrNull()?.let { return "up:${continueWatchingKey(it)}" }
    return when (val row = model.rows.firstOrNull()) {
        is TvHomeRow.Catalog -> row.items.firstOrNull()?.let { "${row.key}:${it.stableKey()}" }
        is TvHomeRow.Folders -> row.collection.folders.firstOrNull()?.let { "${row.key}:${it.id}" }
        null -> null
    }
}

private fun heroOf(item: MetaPreview): HeroInfo = HeroInfo(
    key = item.stableKey(),
    title = item.name,
    logo = item.logo,
    backdrop = item.banner ?: item.landscapePoster ?: item.poster,
    metaLine = listOfNotNull(
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay),
        item.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
        item.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", "),
    ).joinToString("  ·  ").ifBlank { null },
    description = item.description,
)

private fun heroOf(item: ContinueWatchingItem, subtitle: String?): HeroInfo = HeroInfo(
    key = "cw:${continueWatchingKey(item)}",
    title = item.title,
    logo = item.logo,
    backdrop = item.background ?: item.imageUrl ?: item.poster,
    metaLine = subtitle,
    description = item.pauseDescription,
)
