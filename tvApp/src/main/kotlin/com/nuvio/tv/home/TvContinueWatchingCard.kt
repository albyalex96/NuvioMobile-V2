package com.nuvio.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.format.formatEpisodeCodeWithSelectedFormat
import com.nuvio.app.core.ui.EpisodeCodeChip
import com.nuvio.app.core.ui.MediaInfoChip
import com.nuvio.app.core.ui.MediaInfoChipStyle
import com.nuvio.app.core.format.formatReleaseDateWithoutYear
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import com.nuvio.app.tv.tvContinueWatchingArtwork
import com.nuvio.app.tv.tvShouldBlurContinueWatchingArtwork
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvCardMetrics
import com.nuvio.tv.ui.TvRemoteImage
import com.nuvio.tv.ui.TvRowProgressBar
import com.nuvio.tv.ui.tvRowCardSurface
import kotlinx.coroutines.delay

// Colors and timings of NuvioTV's ContinueWatchingCard.
private val PosterTitleBlockHeight = 30.dp
private const val TITLE_MARQUEE_DELAY_MS = 1_000L
private const val PosterTitleScale = 0.85f

/**
 * "Continue watching" card of the TV home, ported from NuvioTV: [ContinueWatchingSectionStyle.Card]
 * (16:9 artwork with the text on it), [ContinueWatchingSectionStyle.Wide] (poster strip + details)
 * and [ContinueWatchingSectionStyle.Poster] (catalog sized poster, title below).
 */
@Composable
fun TvContinueWatchingCard(
    item: ContinueWatchingItem,
    style: ContinueWatchingSectionStyle,
    metrics: TvCardMetrics,
    useEpisodeThumbnails: Boolean,
    blurNextUp: Boolean,
    upcoming: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // Hold the title still until the focus has settled, then let it scroll if it overflows.
    var marqueeActive by remember { mutableStateOf(false) }
    LaunchedEffect(focused, item.title) {
        marqueeActive = false
        if (focused) {
            delay(TITLE_MARQUEE_DELAY_MS)
            marqueeActive = true
        }
    }
    val artwork = remember(item, style, useEpisodeThumbnails) {
        item.tvContinueWatchingArtwork(style, useEpisodeThumbnails)
    }
    val blurred = remember(item, artwork, blurNextUp, useEpisodeThumbnails) {
        item.tvShouldBlurContinueWatchingArtwork(blurNextUp, useEpisodeThumbnails, artwork)
    }
    val text = continueWatchingText(item, short = style != ContinueWatchingSectionStyle.Card, upcoming = upcoming)
    val shape = metrics.shape
    val surface = Modifier.tvRowCardSurface(
        shape = shape,
        depthSurface = NuvioCardDepthSurface.ContinueWatching,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocusChange = { hasFocus ->
            focused = hasFocus
            if (hasFocus) onFocused()
        },
    )
    when (style) {
        ContinueWatchingSectionStyle.Card -> Box(
            modifier = modifier
                .width(metrics.continueWatchingWidth)
                .height(metrics.continueWatchingHeight)
                .then(surface)
                .background(MaterialTheme.nuvio.colors.surfaceCard),
        ) {
            CardArtwork(url = artwork, blurred = blurred)
            val background = MaterialTheme.nuvio.colors.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.73f to background.copy(alpha = 0.7f),
                            1f to background.copy(alpha = 0.95f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                text.episode?.let { episode ->
                    Text(
                        text = episode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textPrimary,
                    )
                }
                MarqueeTitle(text = item.title, active = marqueeActive, style = titleSmall())
                text.episodeTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ContinueWatchingBadge(text = text.badge, style = text.badgeStyle, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (text.progress != null) {
                TvRowProgressBar(
                    progress = text.progress,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        ContinueWatchingSectionStyle.Wide -> Row(
            modifier = modifier
                .width(metrics.continueWatchingWidth)
                .height(metrics.continueWatchingHeight)
                .then(surface)
                .background(MaterialTheme.nuvio.colors.surfaceCard),
        ) {
            // The strip follows the card height so the poster is never cropped.
            Box(modifier = Modifier.width(metrics.continueWatchingHeight * (2f / 3f)).fillMaxHeight()) {
                CardArtwork(url = artwork, blurred = blurred)
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            MarqueeTitle(text = item.title, active = marqueeActive, style = titleSmall())
                        }
                        if (text.progress == null) {
                            ContinueWatchingBadge(text = text.badge, style = text.badgeStyle, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    // Both lines are always laid out so a movie keeps the same shape as a show.
                    if (text.episode != null) {
                        EpisodeCodeChip(
                            text = text.episode,
                            textSize = MaterialTheme.typography.labelSmall.fontSize,
                            radius = 4.dp,
                        )
                    } else {
                        Text(
                            text = "",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            minLines = 1,
                        )
                    }
                    MarqueeTitle(
                        text = text.episodeTitle.orEmpty(),
                        active = marqueeActive,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                    )
                }
                // The bar keeps its space even with no progress so every card lays out identically.
                Column(
                    modifier = Modifier.alpha(if (text.progress != null) 1f else 0f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TvRowProgressBar(progress = text.progress ?: 0f)
                    Text(
                        text = text.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ContinueWatchingSectionStyle.Poster -> Column(modifier = modifier.width(metrics.continueWatchingWidth)) {
            // Text under the artwork: the ring is drawn by the artwork only.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.continueWatchingHeight)
                    .then(surface)
                    .background(MaterialTheme.nuvio.colors.surfaceCard),
            ) {
                CardArtwork(url = artwork, blurred = blurred)
                // Poster cards only show next-up / new episode badges, not the remaining time.
                if (text.progress == null) {
                    ContinueWatchingBadge(text = text.badge, style = text.badgeStyle, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.nuvio.colors.background.copy(alpha = 0.7f))
                            .padding(2.dp),
                    ) {
                        TvRowProgressBar(progress = text.progress)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    .height(PosterTitleBlockHeight),
                verticalAlignment = Alignment.Top,
            ) {
                val base = titleSmall()
                val scaled = base.fontSize * PosterTitleScale
                Text(
                    text = item.title,
                    style = base.copy(fontSize = scaled, lineHeight = scaled * 1.1f),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                text.episode?.let { episode ->
                    EpisodeCodeChip(
                        text = episode,
                        textSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.padding(start = 4.dp),
                        radius = 4.dp,
                    )
                }
            }
        }
    }
}

private class ContinueWatchingText(
    val episode: String?,
    val episodeTitle: String?,
    val badge: String,
    val badgeStyle: MediaInfoChipStyle,
    val progress: Float?,
)

@Composable
private fun continueWatchingText(item: ContinueWatchingItem, short: Boolean, upcoming: Boolean): ContinueWatchingText {
    val season = item.seasonNumber
    val episodeNumber = item.episodeNumber
    val episode = if (season != null && episodeNumber != null) {
        formatEpisodeCodeWithSelectedFormat(season, episodeNumber)
            ?: stringResource(R.string.tv_details_episode_code, season, episodeNumber)
    } else {
        null
    }
    val progress = item.progressFraction.takeIf { !item.isNextUp && it > 0f }
    val badge = when {
        item.isNewSeasonRelease -> stringResource(R.string.tv_cw_new_season)
        item.isReleaseAlert -> stringResource(if (short) R.string.tv_cw_new_episode_short else R.string.tv_cw_new_episode)
        upcoming -> item.released?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateWithoutYear)
            ?: stringResource(R.string.tv_cw_upcoming)
        progress != null -> remainingLabel(item)
        else -> stringResource(if (short) R.string.tv_cw_next_up_short else R.string.tv_cw_next_up)
    }
    val badgeStyle = when {
        item.isNewSeasonRelease -> MediaInfoChipStyle.NewSeason
        item.isReleaseAlert -> MediaInfoChipStyle.NewEpisode
        upcoming -> MediaInfoChipStyle.AirDate
        progress != null && item.durationMs <= 0L -> MediaInfoChipStyle.WatchedPercent
        progress != null -> MediaInfoChipStyle.TimeRemaining
        else -> MediaInfoChipStyle.UpNext
    }
    return ContinueWatchingText(
        episode = episode,
        episodeTitle = item.episodeTitle?.takeIf { it.isNotBlank() },
        badge = badge,
        badgeStyle = badgeStyle,
        progress = progress,
    )
}

/** "1h 20m left" / "35m left", or the watched percentage when the duration is unknown. */
@Composable
private fun remainingLabel(item: ContinueWatchingItem): String {
    if (item.durationMs <= 0L) {
        val percent = (item.progressFraction * 100f).toInt().coerceIn(0, 100)
        return if (percent > 0) stringResource(R.string.tv_cw_percent_watched, percent) else stringResource(R.string.tv_cw_resume)
    }
    val position = item.resumePositionMs.takeIf { it > 0L }
        ?: (item.durationMs * item.progressFraction).toLong()
    val totalMinutes = (item.durationMs - position).coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60
    return if (hours > 0) {
        stringResource(R.string.tv_cw_hours_min_left, hours.toInt(), (totalMinutes % 60).toInt())
    } else {
        stringResource(R.string.tv_cw_min_left, totalMinutes.coerceAtLeast(1).toInt())
    }
}

@Composable
private fun CardArtwork(url: String?, blurred: Boolean) {
    TvRemoteImage(url = url, modifier = Modifier.fillMaxSize().then(if (blurred) Modifier.blur(12.dp) else Modifier))
}

@Composable
private fun ContinueWatchingBadge(text: String, style: MediaInfoChipStyle, modifier: Modifier = Modifier) {
    MediaInfoChip(
        text = text,
        style = style,
        textSize = MaterialTheme.typography.labelSmall.fontSize,
        modifier = modifier,
        radius = 4.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 4.dp,
    )
}

@Composable
private fun MarqueeTitle(
    text: String,
    active: Boolean,
    style: TextStyle,
    color: Color = MaterialTheme.nuvio.colors.textPrimary,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (active) Modifier.basicMarquee(initialDelayMillis = 0) else Modifier,
    )
}

@Composable
private fun titleSmall(): TextStyle =
    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
