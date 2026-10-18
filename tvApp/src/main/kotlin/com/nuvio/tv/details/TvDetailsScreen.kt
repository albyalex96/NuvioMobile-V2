package com.nuvio.tv.details

import com.nuvio.app.core.ui.primaryActionContentColor
import com.nuvio.app.core.ui.primaryActionSurface
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.tv.TvDetailsModel
import com.nuvio.app.tv.TvExternalRatingsRow
import com.nuvio.app.tv.TvPendingDetailAction
import com.nuvio.app.tv.TvSeason
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.showsLandscape
import com.nuvio.tv.ui.rememberTvPosterCardStyle
import com.nuvio.tv.ui.landscapeArtwork
import com.nuvio.tv.ui.TvLandscapeCard
import com.nuvio.tv.ui.TvPosterCard
import com.nuvio.tv.ui.TvSectionTitle
import com.nuvio.tv.ui.tvFocusable
import kotlinx.coroutines.delay

private val log = Logger.withTag("TvDetails")

// Layout of NuvioTV's detail page (HeroSection, EpisodesSection, CastSection) at 960dp width.
private val HeroHorizontalPadding = 48.dp
private val ActionButtonSize = 48.dp
private val EpisodeCardShape = RoundedCornerShape(12.dp)
private val SeasonTabShape = RoundedCornerShape(20.dp)
private val PlayButtonShape = RoundedCornerShape(32.dp)
// NuvioTV `CastSection`: 100dp photo in a 150dp column.
private val CastPhotoSize = 100.dp
private val CastItemWidth = 150.dp
private const val SEASON_FOCUS_SELECT_DELAY_MS = 150L

/** TV detail page, recreated from NuvioTV: full-screen backdrop, bottom-aligned hero, seasons and episodes below. */
@Composable
fun TvDetailsScreen(model: TvDetailsModel) {
    val meta = model.meta
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrolledPastHero by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // "Watch a random episode" from a card menu: play one as soon as the episodes are loaded.
    LaunchedEffect(meta.id, model.seasons) {
        if (model.seasons.isEmpty()) return@LaunchedEffect
        if (!TvPendingDetailAction.consumeRandomEpisode(meta.id)) return@LaunchedEffect
        val episodes = model.seasons
            .filter { it.number > 0 }
            .flatMap { it.episodes }
            .filter { it.available }
        val pick = episodes.filterNot(model.isEpisodeWatched).randomOrNull() ?: episodes.randomOrNull()
        if (pick == null) {
            log.w { "No episode available for a random pick" }
        } else {
            log.i { "Playing a random episode: S${pick.season}E${pick.episode}" }
            model.onEpisodeClick(pick)
        }
    }
    LaunchedEffect(meta.id) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { playRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        log.w { "Could not focus the play button" }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.nuvio.colors.background),
    ) {
        val heroHeight = maxHeight
        DetailsBackdrop(url = meta.background ?: meta.poster, dimmed = scrolledPastHero)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            item(key = "hero") {
                HeroSection(
                    model = model,
                    playRequester = playRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                )
            }
            if (model.seasons.isNotEmpty()) {
                item(key = "episodes") {
                    EpisodesSection(model)
                }
            }
            if (meta.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(cast = meta.cast)
                }
            }
            if (meta.collectionItems.isNotEmpty()) {
                item(key = "collection") {
                    PosterRow(
                        title = meta.collectionName ?: stringResource(R.string.tv_details_collection),
                        items = meta.collectionItems,
                        onClick = model.onOpenMeta,
                    )
                }
            }
            if (meta.moreLikeThis.isNotEmpty()) {
                item(key = "more_like_this") {
                    PosterRow(
                        title = stringResource(R.string.tv_details_more_like_this),
                        items = meta.moreLikeThis,
                        onClick = model.onOpenMeta,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsBackdrop(url: String?, dimmed: Boolean) {
    val background = MaterialTheme.nuvio.colors.background
    val alpha by animateFloatAsState(
        targetValue = if (dimmed) 0.15f else 1f,
        animationSpec = tween(if (dimmed) 300 else 800),
        label = "tvDetailsBackdrop",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                alpha = alpha,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Left and bottom fades so the hero text stays readable (NuvioTV backdrop gradients).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background.copy(alpha = 0.95f),
                        0.35f to background.copy(alpha = 0.75f),
                        0.65f to background.copy(alpha = 0.15f),
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
                        0.55f to Color.Transparent,
                        0.85f to background.copy(alpha = 0.7f),
                        1f to background,
                    ),
                ),
        )
    }
}

@Composable
private fun HeroSection(
    model: TvDetailsModel,
    playRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val meta = model.meta
    var logoFailed by remember(meta.logo) { mutableStateOf(false) }
    val creditLine = when {
        meta.director.isNotEmpty() -> if (model.isSeries) {
            stringResource(R.string.tv_details_creator, meta.director.joinToString(", "))
        } else {
            stringResource(R.string.tv_details_director, meta.director.joinToString(", "))
        }
        meta.writer.isNotEmpty() -> stringResource(R.string.tv_details_writer, meta.writer.joinToString(", "))
        else -> null
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.Bottom) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HeroHorizontalPadding, end = HeroHorizontalPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (!meta.logo.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = meta.logo,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(0.4f)
                        .padding(bottom = 16.dp),
                )
            } else {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(bottom = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusRestorer(playRequester),
            ) {
                PlayButton(
                    text = model.primaryActionLabel,
                    enabled = model.primaryActionEnabled,
                    progress = model.primaryActionProgress,
                    onClick = model.onPrimaryAction,
                    onLongClick = model.onPrimaryActionManual,
                    modifier = Modifier.focusRequester(playRequester),
                )
                ActionIconButton(
                    icon = if (model.isSaved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (model.isSaved) R.string.tv_details_in_library else R.string.tv_details_add_to_library,
                    ),
                    onClick = model.onToggleSaved,
                )
                ActionIconButton(
                    icon = if (model.isWatched) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(
                        if (model.isWatched) R.string.tv_details_watched else R.string.tv_details_mark_watched,
                    ),
                    selected = model.isWatched,
                    onClick = model.onToggleWatched,
                )
                model.onPrimaryActionManual?.let { manual ->
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.tv_details_choose_source),
                        onClick = manual,
                    )
                }
                model.onShuffleClick?.let { shuffle ->
                    ActionIconButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = stringResource(
                            if (model.shuffleActive) R.string.tv_details_shuffle_on else R.string.tv_details_shuffle,
                        ),
                        selected = model.shuffleActive,
                        onClick = shuffle,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!creditLine.isNullOrBlank()) {
                Text(
                    text = creditLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // MDBList ratings (NuvioTV `MDBListRatingsRow`), between the credits and the plot.
            if (model.showExternalRatings && meta.externalRatings.isNotEmpty()) {
                TvExternalRatingsRow(
                    ratings = meta.externalRatings,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (!meta.description.isNullOrBlank()) {
                Text(
                    text = meta.description.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.nuvio.colors.textPrimary.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(bottom = 12.dp),
                )
            }

            MetaInfoRows(model)
        }
    }
}

/** White pill like NuvioTV's `PlayButton`; OK held (or Menu) opens the manual source choice. */
@Composable
private fun PlayButton(
    text: String,
    enabled: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = primaryActionContentColor(enabled)
    val resume = progress != null && progress > 0f
    Box(
        modifier = modifier
            .focusProperties { up = FocusRequester.Cancel }
            .tvFocusable(
                shape = PlayButtonShape,
                onClick = onClick,
                onLongClick = onLongClick,
                focusedScale = 1.05f,
                enabled = enabled,
                onFocusChange = { focused = it },
            )
            // Same gradient as the phone's main buttons; the accent glow lights up with the focus.
            .primaryActionSurface(shape = PlayButtonShape, enabled = enabled, glow = focused),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp),
        ) {
            Icon(
                imageVector = if (resume) Icons.Rounded.PlayCircle else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 22.dp, vertical = 5.dp)
                    .width(64.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.nuvio.colors.accent),
                )
            }
        }
    }
}

/** Round icon button of the hero (NuvioTV `ActionIconButton`). */
@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.nuvio.colors.accent
    val container = when {
        focused -> accent
        selected -> Color.White
        else -> MaterialTheme.nuvio.colors.surfaceCard
    }
    val content = when {
        focused -> MaterialTheme.nuvio.colors.onAccent
        selected -> Color.Black
        else -> MaterialTheme.nuvio.colors.textPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ActionButtonSize)
            .focusProperties { up = FocusRequester.Cancel }
            .tvFocusable(
                shape = CircleShape,
                onClick = onClick,
                focusedScale = 1.08f,
                onFocusChange = { focused = it },
            )
            .background(container, CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MetaInfoRows(model: TvDetailsModel) {
    val meta = model.meta
    val secondary = MaterialTheme.nuvio.colors.textSecondary
    // Like NuvioTV, the IMDb score is not repeated when the MDBList row already shows it.
    val imdbInRatingsRow = model.showExternalRatings && meta.externalRatings.any { it.source == "imdb" }
    val primaryItems = buildList {
        meta.genres.take(3).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" • ")) }
        meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(formatReleaseDateForDisplay(it)) }
        if (!imdbInRatingsRow) meta.imdbRating?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
    }
    val status = when (meta.status?.trim()?.lowercase()) {
        null, "" -> null
        "ended" -> stringResource(R.string.tv_details_status_ended)
        "continuing", "returning series" -> stringResource(R.string.tv_details_status_continuing)
        "canceled", "cancelled" -> stringResource(R.string.tv_details_status_cancelled)
        else -> meta.status?.trim()
    }?.uppercase()
    val secondaryItems = listOfNotNull(
        meta.runtime?.takeIf { it.isNotBlank() },
        meta.country?.takeIf { it.isNotBlank() },
        meta.language?.takeIf { it.isNotBlank() }?.uppercase(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (primaryItems.isNotEmpty()) {
            DividedRow(items = primaryItems, color = secondary, large = true)
        }
        val ageRating = meta.ageRating?.takeIf { it.isNotBlank() }
        if (ageRating != null || status != null || secondaryItems.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ageRating?.let { MetaBadge(it, MaterialTheme.nuvio.colors.textSecondary) }
                status?.let { MetaBadge(it, MaterialTheme.nuvio.colors.textPrimary) }
                if (secondaryItems.isNotEmpty()) {
                    DividedRow(items = secondaryItems, color = MaterialTheme.nuvio.colors.textPrimary, large = false)
                }
            }
        }
    }
}

@Composable
private fun DividedRow(items: List<String>, color: Color, large: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Text(
                text = item,
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.nuvio.colors.textMuted),
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EpisodesSection(model: TvDetailsModel) {
    // Specials (season 0) go last, like NuvioTV.
    val seasons = remember(model.seasons) {
        model.seasons.filter { it.number > 0 }.sortedBy { it.number } + model.seasons.filter { it.number <= 0 }
    }
    var selectedSeason by rememberSaveable(model.meta.id) {
        mutableStateOf(
            model.primaryActionSeason?.takeIf { season -> seasons.any { it.number == season } }
                ?: seasons.first().number,
        )
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.first()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (seasons.size > 1) {
            SeasonTabs(
                seasons = seasons,
                selectedSeason = season.number,
                onSeasonSelected = { selectedSeason = it },
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
        EpisodesRow(model = model, season = season)
    }
}

/** Season pills: moving the focus on a season selects it after a short pause (NuvioTV behavior). */
@Composable
private fun SeasonTabs(
    seasons: List<TvSeason>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
) {
    var pendingSeason by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(SEASON_FOCUS_SELECT_DELAY_MS)
        onSeasonSelected(target)
        pendingSeason = null
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(seasons, key = TvSeason::number) { item ->
            var focused by remember { mutableStateOf(false) }
            val selected = item.number == selectedSeason
            val accent = MaterialTheme.nuvio.colors.accent
            Box(
                modifier = Modifier
                    .onFocusChanged { state ->
                        if (state.isFocused && !selected) pendingSeason = item.number
                    }
                    .tvFocusable(
                        shape = SeasonTabShape,
                        onClick = { onSeasonSelected(item.number) },
                        focusedScale = 1f,
                        onFocusChange = { focused = it },
                    )
                    .background(
                        when {
                            focused -> accent
                            selected -> Color.White.copy(alpha = 0.16f)
                            else -> MaterialTheme.nuvio.colors.surfaceCard
                        },
                        SeasonTabShape,
                    ),
            ) {
                Text(
                    text = seasonLabel(item.number),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        focused -> MaterialTheme.nuvio.colors.onAccent
                        selected -> MaterialTheme.nuvio.colors.textPrimary
                        else -> MaterialTheme.nuvio.colors.textSecondary
                    },
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodesRow(model: TvDetailsModel, season: TvSeason) {
    val metrics = rememberEpisodeCardMetrics()
    val listState = rememberLazyListState()
    val targetIndex = season.episodes.indexOfFirst { episode ->
        season.number == model.primaryActionSeason && episode.episode == model.primaryActionEpisode
    }
    LaunchedEffect(season.number) {
        listState.scrollToItem(targetIndex.coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = metrics.rowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(season.episodes, key = { episode -> "${episode.season}:${episode.episode}:${episode.id}" }) { episode ->
            EpisodeCard(model = model, episode = episode, metrics = metrics)
        }
    }
}

/** Episode card size of NuvioTV (`rememberEpisodeCardMetrics`), which grows on wider TV canvases. */
private data class EpisodeCardMetrics(val width: Dp, val height: Dp, val spacing: Dp, val rowPadding: Dp)

@Composable
private fun rememberEpisodeCardMetrics(): EpisodeCardMetrics {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp) {
        when {
            screenWidthDp >= 1300 -> EpisodeCardMetrics(width = 400.dp, height = 263.dp, spacing = 20.dp, rowPadding = 56.dp)
            screenWidthDp >= 1000 -> EpisodeCardMetrics(width = 360.dp, height = 235.dp, spacing = 18.dp, rowPadding = 52.dp)
            else -> EpisodeCardMetrics(width = 320.dp, height = 207.dp, spacing = 16.dp, rowPadding = 48.dp)
        }
    }
}

/** Episode card of NuvioTV: artwork with a bottom scrim, episode badge, title, plot and info row. */
@Composable
private fun EpisodeCard(model: TvDetailsModel, episode: MetaVideo, metrics: EpisodeCardMetrics) {
    var focused by remember { mutableStateOf(false) }
    val progress = model.episodeProgress(episode)
    val watched = model.isEpisodeWatched(episode)
    val code = formatEpisodeCodeWithSelectedFormat(episode.season, episode.episode)
        ?: if (episode.season != null && episode.episode != null) {
            stringResource(R.string.tv_details_episode_code, episode.season ?: 0, episode.episode ?: 0)
        } else {
            null
        }
    val date = episode.released?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)
    val runtime = episode.runtime?.takeIf { it > 0 }?.let { stringResource(R.string.tv_details_runtime_minutes, it) }
    val rating = episode.rating?.takeIf { it > 0.0 }?.let { "%.1f".format(it) }
    val textShadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 1f)
    val muted = MaterialTheme.nuvio.colors.textSecondary

    Box(
        modifier = Modifier
            .width(metrics.width)
            .height(metrics.height)
            .tvFocusable(
                shape = EpisodeCardShape,
                onClick = { model.onEpisodeClick(episode) },
                onLongClick = model.onEpisodeManualClick?.let { manual -> { manual(episode) } },
                focusedScale = 1f,
                onFocusChange = { focused = it },
            )
            .background(MaterialTheme.nuvio.colors.surfaceCard, EpisodeCardShape),
    ) {
        AsyncImage(
            model = episode.thumbnail ?: model.meta.background,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            alpha = if (episode.available) 1f else 0.5f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.2f to Color.Black.copy(alpha = 0.04f),
                        0.38f to Color.Black.copy(alpha = 0.26f),
                        0.54f to Color.Black.copy(alpha = 0.56f),
                        0.7f to Color.Black.copy(alpha = 0.76f),
                        0.86f to Color.Black.copy(alpha = 0.88f),
                        1f to Color.Black.copy(alpha = if (focused) 0.95f else 0.9f),
                    ),
                ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
        ) {
            if (code != null) {
                Text(
                    text = code.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, shadow = textShadow),
                color = MaterialTheme.nuvio.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val overview = episode.overview?.trim().orEmpty()
            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow, lineHeight = 17.sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (runtime != null || rating != null || date != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    runtime?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                            Text(text = it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        }
                    }
                    rating?.let {
                        Text(
                            text = "IMDb $it",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFF5C518),
                            maxLines = 1,
                        )
                    }
                    date?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (progress != null && progress > 0f && !watched) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(MaterialTheme.nuvio.colors.accent),
                )
            }
        }
        when {
            !episode.available -> Text(
                text = stringResource(R.string.tv_details_unavailable).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .background(Color(0xCC5D4037), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            watched -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.nuvio.colors.accent),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.tv_watched),
                    tint = MaterialTheme.nuvio.colors.onAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            (progress ?: 0f) < 0.02f -> {
                val dashColor = muted.copy(alpha = 0.9f)
                Canvas(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .size(28.dp),
                ) {
                    drawCircle(
                        color = dashColor,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CastSection(cast: List<MetaPerson>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        TvSectionTitle(stringResource(R.string.tv_details_cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = HeroHorizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            items(cast.take(30), key = { person -> "${person.tmdbId}:${person.name}:${person.role}" }) { person ->
                CastMemberItem(person)
            }
        }
    }
}

/** Round cast photo with name and role (NuvioTV `CastMemberItem`); focusable to scroll the row. */
@Composable
private fun CastMemberItem(person: MetaPerson) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(CastItemWidth),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CastPhotoSize)
                .tvFocusable(shape = CircleShape, onClick = {}, focusedScale = 1.06f)
                .background(MaterialTheme.nuvio.colors.surfaceCard, CircleShape),
        ) {
            if (!person.photo.isNullOrBlank()) {
                AsyncImage(
                    model = person.photo,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.nuvio.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.nuvio.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    onClick: (MetaPreview) -> Unit,
) {
    val posterStyle = rememberTvPosterCardStyle()
    val landscape = posterStyle.showsLandscape(items)
    TvContentRow(title = title, modifier = Modifier.padding(top = 18.dp)) {
        items(items, key = MetaPreview::stableKey) { item ->
            if (landscape) {
                val artwork = posterStyle.landscapeArtwork(item)
                TvLandscapeCard(
                    imageUrl = artwork.imageUrl,
                    logoUrl = artwork.logoUrl,
                    fitImage = artwork.fitImage,
                    title = item.name,
                    subtitle = null,
                    onClick = { onClick(item) },
                )
            } else {
                TvPosterCard(
                    imageUrl = item.poster,
                    title = item.name,
                    watched = false,
                    width = TvDimens.basePosterWidth,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

@Composable
private fun seasonLabel(number: Int): String =
    if (number <= 0) {
        stringResource(R.string.tv_details_specials)
    } else {
        stringResource(R.string.tv_details_season, number)
    }
