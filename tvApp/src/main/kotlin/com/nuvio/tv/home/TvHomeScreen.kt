package com.nuvio.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.nuvio.tv.ui.TvActionButton
import com.nuvio.tv.ui.TvCardShape
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvLandscapeCard
import com.nuvio.tv.ui.TvMessage
import com.nuvio.tv.ui.TvPosterAspectRatio
import com.nuvio.tv.ui.TvPosterCard
import com.nuvio.tv.ui.TvSquareTileSize
import com.nuvio.tv.ui.tvFocusable
import kotlinx.coroutines.delay

private val log = Logger.withTag("TvHome")

/** Delay before the hero follows the focused card, so fast scrolling does not flicker. */
private const val HERO_FOLLOW_DELAY_MS = 220L

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
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }
    var lastFocusedKey by rememberSaveable { mutableStateOf<String?>(null) }

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

    val firstKey = remember(model) { firstFocusableKey(model) }
    LaunchedEffect(firstKey) {
        val target = lastFocusedKey?.let(requesters::get) ?: firstKey?.let(requesters::get) ?: return@LaunchedEffect
        // The lazy rows compose their first items a few frames after the list.
        repeat(10) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not move the initial focus into the home rows" }
    }

    fun onCardFocused(key: String, hero: HeroInfo) {
        lastFocusedKey = key
        focusedHero = hero
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // NuvioTV keeps the rows in the bottom 52% of the screen.
        val heroHeight = maxHeight * 0.48f
        HeroBackdrop(url = displayedHero?.backdrop)

        Column(modifier = Modifier.fillMaxSize()) {
            HeroInfoPanel(
                hero = displayedHero,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
            )
            val continueWatchingSubtitle: @Composable (ContinueWatchingItem) -> String = { item ->
                localizedContinueWatchingSubtitle(item, compact = true)
            }
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(TvDimens.rowGap - TvDimens.rowVerticalPadding * 2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRestorer(),
            ) {
                if (model.continueWatching.isNotEmpty()) {
                    item(key = "continue_watching", contentType = "landscape_row") {
                        ContinueWatchingRow(
                            title = stringResource(R.string.tv_home_continue_watching),
                            items = model.continueWatching,
                            keyPrefix = "cw",
                            model = model,
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
                            model = model,
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
                            requesterFor = ::requesterFor,
                            onFocused = ::onCardFocused,
                        )
                        is TvHomeRow.Folders -> FoldersRow(
                            row = row,
                            model = model,
                            requesterFor = ::requesterFor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(url: String?) {
    val background = MaterialTheme.nuvio.colors.background
    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = url,
            animationSpec = tween(450),
            label = "tvHomeBackdrop",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.72f),
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
    model: TvHomeModel,
    subtitle: @Composable (ContinueWatchingItem) -> String,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    TvContentRow(title = title) {
        items(items, key = { item -> "$keyPrefix:${continueWatchingKey(item)}" }) { item ->
            val key = "$keyPrefix:${continueWatchingKey(item)}"
            val itemSubtitle = subtitle(item)
            TvLandscapeCard(
                imageUrl = item.imageUrl ?: item.background ?: item.poster,
                title = item.title,
                subtitle = itemSubtitle,
                progress = item.progressFraction.takeIf { !item.isNextUp && it > 0f },
                badge = when {
                    item.isNewSeasonRelease || item.isReleaseAlert -> stringResource(R.string.tv_new_episode)
                    item.isNextUp -> stringResource(R.string.tv_up_next)
                    else -> null
                },
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
    requesterFor: (String) -> FocusRequester,
    onFocused: (String, HeroInfo) -> Unit,
) {
    val landscape = row.items.firstOrNull()?.posterShape == PosterShape.Landscape
    TvContentRow(title = row.section.title) {
        items(row.items, key = { item -> "${row.key}:${item.stableKey()}" }) { item ->
            val key = "${row.key}:${item.stableKey()}"
            val focusModifier = Modifier.focusRequester(requesterFor(key))
            if (landscape) {
                TvLandscapeCard(
                    imageUrl = item.landscapePoster ?: item.banner ?: item.poster,
                    title = item.name,
                    subtitle = null,
                    watched = model.isPosterWatched(item),
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            } else {
                TvPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    watched = model.isPosterWatched(item),
                    width = if (item.posterShape == PosterShape.Square) TvSquareTileSize else TvDimens.posterWidth,
                    aspectRatio = if (item.posterShape == PosterShape.Square) 1f else TvPosterAspectRatio,
                    onClick = { model.onPosterClick(item) },
                    onLongClick = model.onPosterLongClick?.let { longClick -> { longClick(item) } },
                    onFocused = { onFocused(key, heroOf(item)) },
                    modifier = focusModifier,
                )
            }
        }
        val onCatalogClick = model.onCatalogClick
        if (row.canOpenCatalog && onCatalogClick != null) {
            item(key = "${row.key}:see_all") {
                SeeAllTile(
                    landscape = landscape,
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
    requesterFor: (String) -> FocusRequester,
) {
    val onFolderClick = model.onFolderClick ?: return
    TvContentRow(title = row.collection.title) {
        items(row.collection.folders, key = { folder -> "${row.key}:${folder.id}" }) { folder ->
            val key = "${row.key}:${folder.id}"
            val modifier = Modifier.focusRequester(requesterFor(key))
            val square = folder.tileShape.equals("square", ignoreCase = true)
            if (folder.isPosterTile() || square) {
                TvPosterCard(
                    imageUrl = folder.coverImageUrl,
                    title = folder.title,
                    watched = false,
                    width = if (square) TvSquareTileSize else TvDimens.posterWidth,
                    aspectRatio = if (square) 1f else TvPosterAspectRatio,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    modifier = modifier,
                )
            } else {
                TvLandscapeCard(
                    imageUrl = folder.coverImageUrl,
                    title = folder.title,
                    subtitle = null,
                    onClick = { onFolderClick(row.collection.id, folder.id) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SeeAllTile(landscape: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(if (landscape) TvDimens.landscapeWidth else TvDimens.posterWidth)
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .tvFocusable(shape = TvCardShape, onClick = onClick, onFocusChange = { focused = it })
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
