package com.nuvio.app.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.glass.GlassBarSurface
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.jelly.JellyMotion
import com.nuvio.app.core.ui.jelly.drawJellyGlow
import com.nuvio.app.core.ui.jelly.drawJellyPill
import com.nuvio.app.core.ui.jelly.jellyPillPath
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.visualNavIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** What the glass bar blurs: the page behind it, marked with [tvGlassBackdropSource]. */
class TvGlassBackdrop internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberTvGlassBackdrop(): TvGlassBackdrop {
    val hazeState = rememberHazeState()
    return remember(hazeState) { TvGlassBackdrop(hazeState) }
}

fun Modifier.tvGlassBackdropSource(backdrop: TvGlassBackdrop): Modifier = hazeSource(backdrop.hazeState)

/**
 * Frosted background of a TV panel (NuvioTV "sidebar blur"): blurs the page marked with
 * [tvGlassBackdropSource]; the caller draws its translucent fill on top.
 */
fun Modifier.tvGlassPanelBlur(backdrop: TvGlassBackdrop): Modifier =
    hazeEffect(state = backdrop.hazeState) {
        blurRadius = 24.dp
        noiseFactor = 0f
    }

/**
 * One section of [TvGlassNavigationBar]. [leading] replaces the icon (e.g. the profile avatar);
 * such an item is drawn above the glass and never shows the accent tint.
 */
@Immutable
class TvGlassNavItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: Painter? = null,
    val leading: (@Composable () -> Unit)? = null,
)

private val CompactItemWidth = 68.dp
private val ExpandedItemWidth = 104.dp
private val CompactTrackHeight = 52.dp
private val ExpandedTrackHeight = 66.dp
private val IconSize = 24.dp
private val LabelHeight = 16.dp

/**
 * TV version of the phone's floating glass bar (same glass surface, glow and jelly pill), driven
 * by the D-pad: while the bar has the focus the pill follows the focused section, OK opens it,
 * and when the focus leaves the pill springs back to the open section. Left/right never leave
 * the bar and up is consumed; down is left to the caller.
 *
 * [expanded] shows the section names under the icons ("always expanded"), otherwise icons only
 * ("always compact"). [selectedRequester] is attached to the open section.
 */
@Composable
fun TvGlassNavigationBar(
    items: List<TvGlassNavItem>,
    backdrop: TvGlassBackdrop?,
    expanded: Boolean,
    glowEnabled: Boolean,
    selectedRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    val accentColor = tokens.colors.accent
    val glowStrength by animateFloatAsState(
        targetValue = if (glowEnabled) 1f else 0f,
        animationSpec = tween(420, easing = NuvioTokens.Motion.standard),
        label = "tvGlassGlow",
    )
    val labelFraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(NuvioTokens.Motion.sheetEnterMillis, easing = NuvioTokens.Motion.standard),
        label = "tvGlassLabels",
    )
    var barFocused by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val focusAlpha by animateFloatAsState(
        targetValue = if (barFocused) 1f else 0f,
        animationSpec = tween(180),
        label = "tvGlassFocus",
    )
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedIndex = items.indexOfFirst { it.selected }
    val pillIndex = if (barFocused && focusedIndex in items.indices) focusedIndex else selectedIndex
    val visualPillIndex = visualNavIndex(pillIndex, items.size, isRtl)
    val motion = remember(items.size, isRtl) { JellyMotion(visualPillIndex.coerceAtLeast(0), items.size) }
    val density = LocalDensity.current
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val entryIndex = selectedIndex.coerceAtLeast(0)

    LaunchedEffect(visualPillIndex, items.size) {
        if (visualPillIndex >= 0) motion.select(visualPillIndex)
    }
    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    val itemWidth = lerp(CompactItemWidth, ExpandedItemWidth, labelFraction)
    val trackHeight = lerp(CompactTrackHeight, ExpandedTrackHeight, labelFraction)
    Box(
        modifier = modifier
            .width(itemWidth * items.size + 8.dp)
            .height(trackHeight)
            .onSizeChanged {
                motion.resize(it.width / density.density, it.height / density.density, items.size)
            },
    ) {
        Box(
            Modifier.matchParentSize()
                .clip(RoundedCornerShape(50))
                .drawWithContent {
                    drawContent()
                    drawJellyGlow(motion.frame, accentColor.copy(alpha = accentColor.alpha * glowStrength))
                },
        ) {
            GlassBarSurface(backdrop?.hazeState, Modifier.matchParentSize(), glowStrength)
        }
        Box(
            Modifier.matchParentSize().drawWithContent {
                if (pillIndex >= 0) {
                    clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                        this@drawWithContent.drawContent()
                    }
                } else {
                    drawContent()
                }
            },
        ) {
            GlassTabRow(items, labelFraction, active = false, selectedIndex = selectedIndex)
        }
        if (pillIndex >= 0) {
            Box(
                Modifier.matchParentSize()
                    .clearAndSetSemantics {}
                    .drawWithContent {
                        drawJellyPill(
                            motion.frame,
                            items.size,
                            accentColor.copy(alpha = NuvioTokens.Opacity.selected),
                            accentColor.copy(alpha = accentColor.alpha * glowStrength),
                        ) { drawContent() }
                        if (focusAlpha > 0f) {
                            // Focus ring: the pill is also the D-pad focus indicator.
                            drawPath(
                                path = jellyPillPath(motion.frame, items.size),
                                color = Color.White.copy(alpha = 0.9f * focusAlpha),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    },
            ) {
                GlassTabRow(items, labelFraction, active = true, selectedIndex = selectedIndex)
            }
        }
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .onFocusChanged { state ->
                    barFocused = state.hasFocus
                    if (!state.hasFocus) focusedIndex = -1
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val current = focusedIndex.takeIf { it in items.indices } ?: return@onPreviewKeyEvent false
                    val step = when (event.key) {
                        Key.DirectionLeft -> if (isRtl) 1 else -1
                        Key.DirectionRight -> if (isRtl) -1 else 1
                        Key.DirectionUp -> return@onPreviewKeyEvent true
                        else -> return@onPreviewKeyEvent false
                    }
                    // Left/right stay inside the bar: the edges do not leak into the page below.
                    requesters.getOrNull(current + step)?.let { runCatching { it.requestFocus() } }
                    true
                }
                .focusGroup(),
        ) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (index == entryIndex) Modifier.focusRequester(selectedRequester) else Modifier)
                        .focusRequester(requesters[index])
                        .onFocusChanged { if (it.isFocused) focusedIndex = index }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = item.onClick,
                        )
                        .semantics {
                            role = Role.Tab
                            selected = item.selected
                            contentDescription = item.label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.leading != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
                                item.leading.invoke()
                            }
                            Spacer(Modifier.height(LabelHeight * labelFraction))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTabRow(
    items: List<TvGlassNavItem>,
    labelFraction: Float,
    active: Boolean,
    selectedIndex: Int,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(4.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val color = when {
                active -> tokens.colors.accent
                index == selectedIndex -> Color.White.copy(alpha = 0.92f)
                else -> tokens.colors.textMuted
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(IconSize).graphicsLayer { translationY = 1.dp.toPx() * labelFraction },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.leading == null && item.icon != null) {
                            Icon(
                                painter = item.icon,
                                contentDescription = null,
                                tint = if (active) Color.White else color,
                                modifier = Modifier.size(IconSize)
                                    .then(if (active) Modifier.gradientMask(palette.accentBrush()) else Modifier),
                            )
                        }
                    }
                    Box(
                        Modifier.height(LabelHeight * labelFraction).fillMaxWidth().clipToBounds().alpha(labelFraction),
                    ) {
                        Text(
                            text = item.label,
                            color = color,
                            style = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = if (active || index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
