package com.nuvio.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
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
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.tv.TvDetailsModel
import com.nuvio.app.tv.TvSeason
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvActionButton
import com.nuvio.tv.ui.TvContentRow
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvLandscapeCard
import com.nuvio.tv.ui.TvPosterCard
import com.nuvio.tv.ui.TvSectionTitle

@Composable
fun TvDetailsScreen(model: TvDetailsModel) {
    val meta = model.meta
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(meta.id) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { playRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.nuvio.colors.background),
    ) {
        DetailsBackdrop(url = meta.background ?: meta.poster)
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            item(key = "header") {
                DetailsHeader(
                    model = model,
                    playRequester = playRequester,
                    modifier = Modifier.heightIn(min = maxHeight * 0.62f),
                )
            }
            if (model.seasons.isNotEmpty()) {
                item(key = "episodes") {
                    EpisodesSection(model)
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
            if (meta.cast.isNotEmpty()) {
                item(key = "cast") {
                    CastLine(names = meta.cast.take(12).map { it.name })
                }
            }
        }
    }
}

@Composable
private fun DetailsBackdrop(url: String?) {
    val background = MaterialTheme.nuvio.colors.background
    Box(modifier = Modifier.fillMaxSize()) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.8f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to background,
                        0.3f to background.copy(alpha = 0.9f),
                        0.62f to background.copy(alpha = 0.2f),
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
                        0.45f to background.copy(alpha = 0.3f),
                        0.8f to background,
                    ),
                ),
        )
    }
}

@Composable
private fun DetailsHeader(
    model: TvDetailsModel,
    playRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val meta = model.meta
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = TvDimens.screenPadding, top = 48.dp, end = TvDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!meta.logo.isNullOrBlank()) {
            AsyncImage(
                model = meta.logo,
                contentDescription = meta.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(96.dp)
                    .widthIn(max = 420.dp),
            )
        } else {
            Text(
                text = meta.name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp),
                color = MaterialTheme.nuvio.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }
        val metaLine = listOfNotNull(
            meta.releaseInfo?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay),
            meta.runtime?.takeIf { it.isNotBlank() },
            meta.ageRating?.takeIf { it.isNotBlank() },
            meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
        ).joinToString("  ·  ")
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.nuvio.colors.textSecondary,
            )
        }
        if (meta.genres.isNotEmpty()) {
            Text(
                text = meta.genres.take(4).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
        if (!meta.description.isNullOrBlank()) {
            Text(
                text = meta.description.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.nuvio.colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 600.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.focusRestorer(playRequester),
        ) {
            TvActionButton(
                label = model.primaryActionLabel,
                icon = Icons.Filled.PlayArrow,
                primary = true,
                enabled = model.primaryActionEnabled,
                progress = model.primaryActionProgress,
                onClick = model.onPrimaryAction,
                onLongClick = model.onPrimaryActionManual,
                modifier = Modifier.focusRequester(playRequester),
            )
            model.onPrimaryActionManual?.let { manual ->
                TvActionButton(
                    label = stringResource(R.string.tv_details_choose_source),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = manual,
                )
            }
            TvActionButton(
                label = stringResource(
                    if (model.isSaved) R.string.tv_details_in_library else R.string.tv_details_add_to_library,
                ),
                icon = if (model.isSaved) Icons.Filled.Check else Icons.Filled.Add,
                selected = model.isSaved,
                onClick = model.onToggleSaved,
            )
            TvActionButton(
                label = stringResource(
                    if (model.isWatched) R.string.tv_details_watched else R.string.tv_details_mark_watched,
                ),
                icon = if (model.isWatched) Icons.Filled.CheckCircle else Icons.Filled.CheckCircleOutline,
                selected = model.isWatched,
                onClick = model.onToggleWatched,
            )
            model.onShuffleClick?.let { shuffle ->
                TvActionButton(
                    label = stringResource(
                        if (model.shuffleActive) R.string.tv_details_shuffle_on else R.string.tv_details_shuffle,
                    ),
                    icon = Icons.Filled.Shuffle,
                    selected = model.shuffleActive,
                    onClick = shuffle,
                )
            }
        }
        if (model.onPrimaryActionManual != null) {
            Text(
                text = stringResource(R.string.tv_details_hint_manual),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }
}

@Composable
private fun EpisodesSection(model: TvDetailsModel) {
    val seasons = model.seasons
    var selectedSeason by rememberSaveable(model.meta.id) {
        mutableStateOf(
            model.primaryActionSeason?.takeIf { season -> seasons.any { it.number == season } }
                ?: seasons.first().number,
        )
    }
    val season = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.first()

    Column(modifier = Modifier.fillMaxWidth()) {
        TvSectionTitle(stringResource(R.string.tv_details_episodes))
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvDimens.screenPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().focusRestorer(),
            ) {
                items(seasons, key = TvSeason::number) { item ->
                    TvActionButton(
                        label = seasonLabel(item.number),
                        selected = item.number == season.number,
                        onClick = { selectedSeason = item.number },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        EpisodeRow(model = model, season = season)
    }
}

@Composable
private fun EpisodeRow(model: TvDetailsModel, season: TvSeason) {
    val listState = rememberLazyListState()
    val targetIndex = season.episodes.indexOfFirst { episode ->
        season.number == model.primaryActionSeason && episode.episode == model.primaryActionEpisode
    }
    LaunchedEffect(season.number) {
        if (targetIndex > 0) listState.scrollToItem(targetIndex)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = TvDimens.screenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.rowSpacing),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(season.episodes, key = { episode -> "${episode.season}:${episode.episode}:${episode.id}" }) { episode ->
            EpisodeCard(model = model, episode = episode)
        }
    }
}

@Composable
private fun EpisodeCard(model: TvDetailsModel, episode: MetaVideo) {
    val code = formatEpisodeCodeWithSelectedFormat(episode.season, episode.episode)
        ?: if (episode.season != null && episode.episode != null) {
            stringResource(R.string.tv_details_episode_code, episode.season ?: 0, episode.episode ?: 0)
        } else {
            null
        }
    val title = listOfNotNull(code, episode.title.takeIf { it.isNotBlank() }).joinToString("  ")
    val subtitle = when {
        !episode.available -> stringResource(R.string.tv_details_unavailable)
        else -> episode.released?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay)
    }
    TvLandscapeCard(
        imageUrl = episode.thumbnail ?: model.meta.background,
        title = title,
        subtitle = subtitle,
        width = 264.dp,
        progress = model.episodeProgress(episode),
        watched = model.isEpisodeWatched(episode),
        dimmed = !episode.available,
        onClick = { model.onEpisodeClick(episode) },
        onLongClick = model.onEpisodeManualClick?.let { manual -> { manual(episode) } },
    )
}

@Composable
private fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    onClick: (MetaPreview) -> Unit,
) {
    TvContentRow(title = title) {
        items(items, key = MetaPreview::stableKey) { item ->
            TvPosterCard(
                imageUrl = item.poster,
                title = item.name,
                watched = false,
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
private fun CastLine(names: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TvSectionTitle(stringResource(R.string.tv_details_cast))
        Text(
            text = names.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.nuvio.colors.textSecondary,
            modifier = Modifier
                .padding(horizontal = TvDimens.screenPadding)
                .width(720.dp),
        )
    }
}

@Composable
private fun seasonLabel(number: Int): String =
    if (number <= 0) {
        stringResource(R.string.tv_details_specials)
    } else {
        stringResource(R.string.tv_details_season, number)
    }
