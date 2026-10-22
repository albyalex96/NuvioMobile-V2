package com.nuvio.app.features.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeTop10Row
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.Top10Kind
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.top10_row_movies
import nuvio.composeapp.generated.resources.top10_row_series
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** Portrait poster frame of the home cards (`NuvioPosterShape.Poster`). */
private const val POSTER_ASPECT_RATIO = 0.675f

/** Part of the rank that slides under the poster. */
private const val RANK_OVERLAP_FRACTION = 0.22f

/** Height of a single-digit rank, relative to the poster height ("10" shrinks to fit its room). */
private const val RANK_HEIGHT_FRACTION = 0.84f

/** Shift to the left of the "1", relative to the width of the rank box. */
private const val RANK_ONE_SHIFT_FRACTION = 0.2f

/** Outline width in the units of [TOP10_RANK_PATHS] (the numbers are 154 units high). */
private const val RANK_OUTLINE_WIDTH = 4f

/**
 * Horizontal room left of the poster for the rank number, relative to the poster width:
 * the two-digit "10" needs about twice the room of a single digit.
 */
fun top10RankSpace(rank: Int, posterWidth: Dp): Dp =
    posterWidth * if (rank >= 10) 1.3f else 0.62f

/**
 * Big outlined rank number of the "Top 10" rows, drawn behind the left edge of the poster.
 * [height] is the height of the poster artwork: the number is centered on it vertically and
 * right-aligned in [width]. The numbers are the vector shapes of [TOP10_RANK_PATHS], fitted on
 * their real bounds.
 */
@Composable
fun Top10RankNumber(
    rank: Int,
    height: Dp,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = remember(rank) {
        TOP10_RANK_PATHS.getOrNull(rank - 1)?.let { PathParser().parsePathString(it).toPath() }
    } ?: return
    val fill = colors.background
    val outline = colors.textPrimary.copy(alpha = 0.6f)

    Canvas(
        modifier = modifier
            .width(width)
            .height(height),
    ) {
        val bounds = shape.getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
        // The outline is centered on the edge: half of it lies outside the bounds.
        val outerWidth = bounds.width + RANK_OUTLINE_WIDTH
        val outerHeight = bounds.height + RANK_OUTLINE_WIDTH
        val scale = min(size.height * RANK_HEIGHT_FRACTION / outerHeight, size.width / outerWidth)
        // The "1" has its stem on the right edge: without the shift half of it hides under the poster.
        val rightShift = if (rank == 1) size.width * RANK_ONE_SHIFT_FRACTION else 0f
        withTransform({
            translate(
                left = size.width - rightShift - (bounds.right + RANK_OUTLINE_WIDTH / 2f) * scale,
                top = (size.height - outerHeight * scale) / 2f - (bounds.top - RANK_OUTLINE_WIDTH / 2f) * scale,
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(path = shape, color = fill)
            drawPath(
                path = shape,
                color = outline,
                style = Stroke(width = RANK_OUTLINE_WIDTH, join = StrokeJoin.Miter),
            )
        }
    }
}

@Composable
fun HomeTop10Section(
    row: HomeTop10Row,
    title: String,
    modifier: Modifier = Modifier,
    sectionPadding: Dp = 16.dp,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (row.items.isEmpty()) return
    val posterCardStyle = rememberPosterCardStyleUiState()
    val ranks = row.items.withIndex().associate { (index, item) -> item.stableKey() to index + 1 }

    NuvioShelfSection(
        title = title,
        entries = row.items,
        modifier = modifier.fillMaxWidth(),
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        key = { item -> item.stableKey() },
    ) { item ->
        val rank = ranks[item.stableKey()] ?: 0
        val posterWidth = posterCardStyle.widthDp.dp
        val posterHeight = if (item.posterShape == PosterShape.Square) posterWidth else posterWidth / POSTER_ASPECT_RATIO
        val rankSpace = top10RankSpace(rank, posterWidth)
        Box {
            Top10RankNumber(
                rank = rank,
                height = posterHeight,
                width = rankSpace + posterWidth * RANK_OVERLAP_FRACTION,
                modifier = Modifier.align(Alignment.TopStart),
            )
            HomePosterCard(
                item = item,
                modifier = Modifier.padding(start = rankSpace),
                forcePortrait = true,
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
    }
}

/** Localized title of a "Top 10" home row. */
@Composable
fun top10RowTitle(kind: Top10Kind): String = stringResource(
    when (kind) {
        Top10Kind.Movies -> Res.string.top10_row_movies
        Top10Kind.Series -> Res.string.top10_row_series
    },
)
