package com.nuvio.tv.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import com.nuvio.tv.settings.TvLayoutSettings

// Scales of NuvioTV's modern home (ModernHomeContent): catalog posters are the base poster
// * 0.84 * 1.08, landscape and "continue watching" cards the base width * 1.24 * 1.34.
private const val PortraitScale = 0.84f * 1.08f
private const val LandscapeScale = 1.24f * 1.34f
private const val LandscapeAspect = 1.77f
private const val WideCardHeightRatio = 0.4f

/** Card sizes of the TV rows, derived from the TV layout settings like NuvioTV does. */
@Immutable
data class TvCardMetrics(
    val posterWidth: Dp,
    val posterHeight: Dp,
    val landscapeWidth: Dp,
    val landscapeHeight: Dp,
    val continueWatchingWidth: Dp,
    val continueWatchingHeight: Dp,
    val cornerRadius: Dp,
    /** Base poster outside the home rows (details "more like this"). */
    val basePosterWidth: Dp,
) {
    val shape = RoundedCornerShape(cornerRadius)

    /** Square tiles are as tall as the posters of the same row. */
    val squareSize: Dp get() = posterHeight

    /** A focused poster that expands to its backdrop keeps its height at 16:9. */
    val expandedPosterWidth: Dp get() = posterHeight * (16f / 9f)
}

fun TvLayoutSettings.cardMetrics(): TvCardMetrics {
    val baseWidth = posterWidthDp.dp
    val baseHeight = posterHeightDp.dp
    val landscapeWidth = baseWidth * LandscapeScale
    val posterWidth = baseWidth * PortraitScale
    val posterHeight = baseHeight * PortraitScale
    val (cwWidth, cwHeight) = when (continueWatchingStyle) {
        ContinueWatchingSectionStyle.Poster -> posterWidth to posterHeight
        // Wide still scales with the poster width so it matches the rest of the row.
        ContinueWatchingSectionStyle.Wide -> (baseWidth * 2.1f).let { it to it * WideCardHeightRatio }
        ContinueWatchingSectionStyle.Card -> landscapeWidth to landscapeWidth / LandscapeAspect
    }
    return TvCardMetrics(
        posterWidth = posterWidth,
        posterHeight = posterHeight,
        landscapeWidth = landscapeWidth,
        landscapeHeight = landscapeWidth / LandscapeAspect,
        continueWatchingWidth = cwWidth,
        continueWatchingHeight = cwHeight,
        cornerRadius = cornerRadiusDp.dp,
        basePosterWidth = baseWidth,
    )
}
