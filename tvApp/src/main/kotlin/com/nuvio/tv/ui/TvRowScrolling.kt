@file:OptIn(ExperimentalFoundationApi::class)

package com.nuvio.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Space kept above the focused card when the rows scroll: the row title stays visible
 * (NuvioTV `MODERN_ROW_HEADER_FOCUS_INSET`).
 */
val TvRowFocusTopInset = 40.dp

/**
 * Vertical scrolling of the home rows (NuvioTV `verticalRowBringIntoViewSpec`): the focused row
 * always lands at the same height, right below [topPadding] with its title visible, instead of
 * the default "just enough to be visible" scroll that stops at the edge and adds small extra
 * moves. One predictable scroll per D-pad press. [topPadding] is read on every call because the
 * hero collapse animates it.
 */
@Composable
fun rememberTvRowsBringIntoViewSpec(listState: LazyListState, topPadding: () -> Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, listState, topPadding) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val target = with(density) { (topPadding() + TvRowFocusTopInset).toPx() }
                val distance = offset - target
                if (abs(distance) < 1f) return 0f
                // First row: nothing above to scroll back to.
                if (distance < 0f && !listState.canScrollBackward) return 0f
                return distance
            }
        }
    }
}

/**
 * Horizontal scrolling of a row (NuvioTV `horizontalBringIntoViewSpec`): the focused card always
 * sits at the start of the row (after [TvDimens.rowPadding] and [extraStartInset]), so every
 * D-pad press is one scroll of one card. [extraStartInset] keeps room for what is drawn before
 * the card (e.g. the Top 10 rank number).
 */
@Composable
fun rememberTvRowBringIntoViewSpec(extraStartInset: Dp = 0.dp): BringIntoViewSpec {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(density, rtl, extraStartInset) {
        val startPx = with(density) { (TvDimens.rowPadding + extraStartInset).toPx() }
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val childSize = abs(size)
                val fits = childSize <= containerSize
                return if (rtl) {
                    val initialTarget = containerSize - startPx
                    val trailingTarget = if (fits && initialTarget < childSize) childSize else initialTarget
                    (offset + size) - trailingTarget
                } else {
                    val leadingTarget = if (fits && containerSize - startPx < childSize) containerSize - childSize else startPx
                    offset - leadingTarget
                }
            }
        }
    }
}
