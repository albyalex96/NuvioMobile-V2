package com.nuvio.app.features.livetv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.focusable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_epg_now
import nuvio.composeapp.generated.resources.live_tv_guide_jump_now
import org.jetbrains.compose.resources.stringResource

internal const val LIVE_TV_GUIDE_SLOT_MILLIS = 30L * 60L * 1000L
internal const val LIVE_TV_GUIDE_WINDOW_MILLIS = 12L * 60L * 60L * 1000L
internal val LiveTvGuideDpPerMinute = 4.dp
internal val LiveTvGuideChannelColumnWidth = 96.dp
private val LiveTvGuideRowHeight = 64.dp
private val LiveTvGuideBlockGap = 3.dp
private val LiveTvGuideBlockRadius = 10.dp
private val LiveTvGuideBlockPaddingH = 8.dp

internal fun liveTvGuideWindowStart(nowMillis: Long): Long =
    (nowMillis / LIVE_TV_GUIDE_SLOT_MILLIS) * LIVE_TV_GUIDE_SLOT_MILLIS - LIVE_TV_GUIDE_SLOT_MILLIS

private fun minutesToDp(millis: Long): Dp = LiveTvGuideDpPerMinute * (millis / 60_000f)

private val LiveTvGuideTimelineWidth: Dp get() = minutesToDp(LIVE_TV_GUIDE_WINDOW_MILLIS)

internal fun liveTvGuideHasPrograms(programs: List<LiveTvEpgProgram>?, windowStart: Long): Boolean {
    val windowEnd = windowStart + LIVE_TV_GUIDE_WINDOW_MILLIS
    return programs?.any { it.endMillis > windowStart && it.startMillis < windowEnd } == true
}

/**
 * Horizontal position of the guide timeline, shared by the ruler and every row.
 *
 * Performance: unlike a shared ScrollState used by many horizontalScroll modifiers, nothing here is
 * written during layout (each horizontalScroll wrote maxValue, invalidating every visible row each
 * time a new row appeared while scrolling vertically). The offset is read only in the draw phase.
 */
@Stable
internal class LiveTvGuideScrollState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var maxPx by mutableFloatStateOf(0f)
        private set
    var viewportPx by mutableFloatStateOf(0f)
        private set

    /**
     * D-pad: time of the selected programme, shared by all rows so that Up/Down keep the same
     * time slot (the programme airing at that time in the next channel is selected).
     */
    var focusTimeMillis by mutableLongStateOf(0L)

    val scrollable = ScrollableState { delta ->
        val old = offsetPx
        offsetPx = (old - delta).coerceIn(0f, maxPx)
        old - offsetPx
    }

    /** Called with the visible timeline width; writing the same value invalidates nothing. */
    fun updateViewport(timelinePx: Float, viewportPx: Float) {
        if (viewportPx != this.viewportPx) this.viewportPx = viewportPx
        val newMax = (timelinePx - viewportPx).coerceAtLeast(0f)
        if (newMax != maxPx) {
            maxPx = newMax
            offsetPx = offsetPx.coerceIn(0f, newMax)
        }
    }

    /** Scrolls the least needed to show [startPx, endPx] (with a margin), for the D-pad. */
    suspend fun reveal(startPx: Float, endPx: Float, marginPx: Float) {
        val target = when {
            startPx - marginPx < offsetPx -> startPx - marginPx
            endPx + marginPx > offsetPx + viewportPx -> minOf(startPx - marginPx, endPx + marginPx - viewportPx)
            else -> return
        }
        animateTo(target)
    }

    fun snapTo(targetPx: Float) {
        offsetPx = targetPx.coerceIn(0f, maxPx)
    }

    suspend fun animateTo(targetPx: Float) {
        val target = targetPx.coerceIn(0f, maxPx)
        scrollable.animateScrollBy(offsetPx - target)
    }
}

/** One pulse animation for the whole guide (instead of one infinite transition per row). */
@Composable
internal fun rememberLiveTvGuidePulse(): State<Float> =
    rememberInfiniteTransition(label = "liveTvGuidePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "liveTvGuidePulseValue",
    )

@Composable
internal fun LiveTvGuideTimeRuler(
    scrollState: LiveTvGuideScrollState,
    windowStart: Long,
    nowMillis: Long,
    pulse: State<Float>,
    onJumpToNow: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = tokens.colors.textMuted)
    val labels = remember(windowStart) {
        val slots = (LIVE_TV_GUIDE_WINDOW_MILLIS / LIVE_TV_GUIDE_SLOT_MILLIS).toInt()
        List(slots) { index -> formatLiveTvEpgTime(windowStart + index * LIVE_TV_GUIDE_SLOT_MILLIS) }
    }
    val density = LocalDensity.current
    val pxPerMinute = with(density) { LiveTvGuideDpPerMinute.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
    ) {
        Box(
            modifier = Modifier
                .width(LiveTvGuideChannelColumnWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            // "Now" chip: scrolls the timeline back to the current time.
            val jumpLabel = stringResource(Res.string.live_tv_guide_jump_now)
            Row(
                modifier = Modifier
                    .liveTvPressScale(onJumpToNow)
                    .clip(tokens.shapes.chip)
                    .background(tokens.colors.overlaySelected)
                    .semantics { contentDescription = jumpLabel }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveTvPulsingDot(size = 6.dp)
                Text(
                    text = stringResource(Res.string.live_tv_epg_now),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .scrollable(scrollState.scrollable, Orientation.Horizontal)
                .drawBehind {
                    val offset = scrollState.offsetPx
                    val slotPx = pxPerMinute * (LIVE_TV_GUIDE_SLOT_MILLIS / 60_000L)
                    val first = (offset / slotPx).toInt().coerceAtLeast(0)
                    val last = ((offset + size.width) / slotPx).toInt().coerceAtMost(labels.lastIndex)
                    for (index in first..last) {
                        val layout = measurer.measure(labels[index], labelStyle, maxLines = 1)
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = index * slotPx - offset + 4.dp.toPx(),
                                y = (size.height - layout.size.height) / 2f,
                            ),
                        )
                    }
                    drawGuideNowLine(
                        x = (nowMillis - windowStart) / 60_000f * pxPerMinute - offset,
                        pulse = pulse.value,
                        withHead = true,
                    )
                },
        )
    }
}

private fun DrawScope.drawGuideNowLine(x: Float, pulse: Float, withHead: Boolean) {
    if (x < -8f || x > size.width + 8f) return
    drawRect(
        color = LiveTvLiveRed.copy(alpha = 0.85f),
        topLeft = Offset(x - 1.dp.toPx(), 0f),
        size = Size(2.dp.toPx(), size.height),
    )
    if (withHead) {
        val radius = 4.dp.toPx()
        val center = Offset(x, size.height - radius - 1.dp.toPx())
        drawCircle(LiveTvLiveRed.copy(alpha = (1f - pulse) * 0.55f), radius * (1f + pulse * 1.4f), center)
        drawCircle(LiveTvLiveRed, radius, center)
    }
}

internal fun LazyListScope.liveTvGuideRows(
    channels: List<LiveTvChannel>,
    epgPrograms: Map<String, List<LiveTvEpgProgram>>,
    scrollState: LiveTvGuideScrollState,
    windowStart: Long,
    nowMillis: Long,
    pulse: State<Float>,
    textMeasurer: TextMeasurer,
    onChannelClick: (LiveTvChannel) -> Unit,
    onProgramClick: (LiveTvChannel, LiveTvEpgProgram) -> Unit,
) {
    items(channels, key = { "guide:${it.id}" }, contentType = { "guide-row" }) { channel ->
        LiveTvGuideRow(
            channel = channel,
            programs = epgPrograms[channel.id].orEmpty(),
            scrollState = scrollState,
            windowStart = windowStart,
            nowMillis = nowMillis,
            pulse = pulse,
            textMeasurer = textMeasurer,
            onChannelClick = { onChannelClick(channel) },
            onProgramClick = { program -> onProgramClick(channel, program) },
        )
    }
}

/** A programme of the window with its precomputed position (in px). */
private class GuideBlock(
    val program: LiveTvEpgProgram,
    val startPx: Float,
    val widthPx: Float,
    private val zone: TimeZone,
) {
    /** Formatted only when the block is drawn for the first time (most never are). */
    val timeText: String by lazy {
        "${formatGuideTime(program.startMillis, zone)} – ${formatGuideTime(program.endMillis, zone)}"
    }
}

private fun formatGuideTime(epochMillis: Long, zone: TimeZone): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

@Composable
private fun LiveTvGuideRow(
    channel: LiveTvChannel,
    programs: List<LiveTvEpgProgram>,
    scrollState: LiveTvGuideScrollState,
    windowStart: Long,
    nowMillis: Long,
    pulse: State<Float>,
    textMeasurer: TextMeasurer,
    onChannelClick: () -> Unit,
    onProgramClick: (LiveTvEpgProgram) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val density = LocalDensity.current
    val pxPerMinute = with(density) { LiveTvGuideDpPerMinute.toPx() }
    val timelinePx = with(density) { LiveTvGuideTimelineWidth.toPx() }
    val windowEnd = windowStart + LIVE_TV_GUIDE_WINDOW_MILLIS
    // XMLTV guides often hold days of programmes: keep only the 12 h window, once per row.
    val blocks = remember(programs, windowStart, pxPerMinute) {
        val zone = TimeZone.currentSystemDefault()
        programs.mapNotNull { program ->
            val start = maxOf(program.startMillis, windowStart)
            val end = minOf(program.endMillis, windowEnd)
            if (end <= start) return@mapNotNull null
            GuideBlock(
                program = program,
                startPx = (start - windowStart) / 60_000f * pxPerMinute,
                widthPx = (end - start) / 60_000f * pxPerMinute,
                zone = zone,
            )
        }
    }

    val airingBlock = remember(blocks, nowMillis) { blocks.firstOrNull { it.program.isAiringAt(nowMillis) } }
    var rowFocused by remember { mutableStateOf(false) }
    val focusScope = rememberCoroutineScope()
    val focusColor = tokens.colors.focusRing
    // Programme selected with the D-pad: the one covering the shared focus time, else the nearest.
    fun selectedBlock(): GuideBlock? {
        if (blocks.isEmpty()) return null
        val time = scrollState.focusTimeMillis.takeIf { it > 0L } ?: nowMillis
        return blocks.firstOrNull { time >= it.program.startMillis && time < it.program.endMillis }
            ?: blocks.minBy { minOf(kotlin.math.abs(it.program.startMillis - time), kotlin.math.abs(it.program.endMillis - time)) }
    }
    fun focusBlock(block: GuideBlock) {
        // Middle of the visible part, so moving on to the next row lands on the same slot.
        scrollState.focusTimeMillis = maxOf(block.program.startMillis, windowStart) +
            (minOf(block.program.endMillis, windowEnd) - maxOf(block.program.startMillis, windowStart)) / 2
        focusScope.launch { scrollState.reveal(block.startPx, block.startPx + block.widthPx, pxPerMinute * 20f) }
    }
    val colors = tokens.colors
    val titleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    val airingTitleStyle = titleStyle.copy(fontWeight = FontWeight.SemiBold)
    val timeStyle = MaterialTheme.typography.labelSmall

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LiveTvGuideRowHeight)
            .padding(vertical = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .width(LiveTvGuideChannelColumnWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onChannelClick)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        // The whole timeline of the row is one drawing: no composable per programme. Only the
        // blocks inside the viewport are measured and drawn (texts are cached by the measurer).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .onSizeChanged { scrollState.updateViewport(timelinePx, it.width.toFloat()) }
                .scrollable(scrollState.scrollable, Orientation.Horizontal)
                // TV / keyboard: the row takes the focus; Left/Right move between programmes,
                // OK opens the selected one. Left on the first programme goes to the channel.
                .onFocusChanged { state ->
                    rowFocused = state.isFocused
                    if (state.isFocused) selectedBlock()?.let(::focusBlock)
                }
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionRight, Key.DirectionLeft -> {
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                            val current = selectedBlock() ?: return@onKeyEvent false
                            val index = blocks.indexOf(current)
                            val next = if (event.key == Key.DirectionRight) blocks.getOrNull(index + 1) else blocks.getOrNull(index - 1)
                            if (next == null) {
                                // Right at the end stays; Left at the start leaves the timeline.
                                event.key == Key.DirectionRight
                            } else {
                                focusBlock(next)
                                true
                            }
                        }

                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyUp) selectedBlock()?.let { onProgramClick(it.program) }
                            true
                        }

                        else -> false
                    }
                }
                .focusable()
                .pointerInput(blocks) {
                    detectTapGestures { position ->
                        val x = position.x + scrollState.offsetPx
                        blocks.firstOrNull { x >= it.startPx && x < it.startPx + it.widthPx }
                            ?.let { onProgramClick(it.program) }
                    }
                }
                .drawBehind {
                    val offset = scrollState.offsetPx
                    val gap = LiveTvGuideBlockGap.toPx()
                    val radius = CornerRadius(LiveTvGuideBlockRadius.toPx())
                    val padH = LiveTvGuideBlockPaddingH.toPx()
                    for (block in blocks) {
                        val left = block.startPx - offset
                        val width = block.widthPx - gap
                        if (left + width < 0f || left > size.width || width <= 0f) continue
                        val airing = block.program.isAiringAt(nowMillis)
                        val past = block.program.endMillis <= nowMillis
                        drawRoundRect(
                            color = if (airing) colors.overlaySelected else colors.surface,
                            topLeft = Offset(left, 0f),
                            size = Size(width, size.height),
                            cornerRadius = radius,
                            alpha = if (past) 0.55f else 1f,
                        )
                        if (airing) {
                            val span = (block.program.endMillis - block.program.startMillis).coerceAtLeast(1L)
                            val elapsed = ((nowMillis - block.program.startMillis).toFloat() / span).coerceIn(0f, 1f)
                            clipRect(left, 0f, left + width * elapsed, size.height) {
                                drawRoundRect(
                                    color = colors.accent.copy(alpha = 0.16f),
                                    topLeft = Offset(left, 0f),
                                    size = Size(width, size.height),
                                    cornerRadius = radius,
                                )
                                drawRect(
                                    color = colors.accent,
                                    topLeft = Offset(left, size.height - 3.dp.toPx()),
                                    size = Size(width, 3.dp.toPx()),
                                )
                            }
                        }
                        if (rowFocused && block === selectedBlock()) {
                            val stroke = 2.5.dp.toPx()
                            drawRoundRect(
                                color = focusColor.copy(alpha = 0.16f),
                                topLeft = Offset(left, 0f),
                                size = Size(width, size.height),
                                cornerRadius = radius,
                            )
                            drawRoundRect(
                                color = focusColor,
                                topLeft = Offset(left + stroke / 2f, stroke / 2f),
                                size = Size(width - stroke, size.height - stroke),
                                cornerRadius = radius,
                                style = Stroke(width = stroke),
                            )
                        }
                        // Fixed text width per block: the measurer cache hits while scrolling.
                        val textLeft = left + padH
                        val textRight = left + width - padH
                        val maxTextWidth = (textRight - textLeft).toInt()
                        if (maxTextWidth < 12) continue
                        var titleLeft = textLeft
                        if (airing) {
                            // The pulsing dot itself is drawn by the overlay layer below.
                            titleLeft += GuideDotRadius.toPx() * 2 + 5.dp.toPx()
                        }
                        val titleWidth = (textRight - titleLeft).toInt()
                        if (titleWidth < 12) continue
                        val title = measureGuideText(
                            textMeasurer,
                            block.program.title,
                            if (airing) airingTitleStyle else titleStyle,
                            titleWidth,
                        )
                        val time = measureGuideText(textMeasurer, block.timeText, timeStyle, titleWidth + (titleLeft - textLeft).toInt())
                        val totalHeight = title.size.height + time.size.height
                        val top = (size.height - totalHeight) / 2f
                        val alpha = if (past) 0.6f else 1f
                        drawText(
                            textLayoutResult = title,
                            color = when {
                                airing -> colors.accent
                                past -> colors.textMuted
                                else -> colors.textPrimary
                            },
                            topLeft = Offset(titleLeft, top),
                            alpha = alpha,
                        )
                        drawText(
                            textLayoutResult = time,
                            color = colors.textMuted,
                            topLeft = Offset(textLeft, top + title.size.height),
                            alpha = alpha,
                        )
                    }
                    drawGuideNowLine(
                        x = (nowMillis - windowStart) / 60_000f * pxPerMinute - offset,
                        pulse = 0f,
                        withHead = false,
                    )
                },
        ) {
            // Separate layer for the pulsing "on air" dot: the animation redraws only this
            // layer every frame, not the blocks and texts of the row.
            if (airingBlock != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer()
                        .drawBehind {
                            val left = airingBlock.startPx - scrollState.offsetPx
                            val width = airingBlock.widthPx - LiveTvGuideBlockGap.toPx()
                            if (left + width < 0f || left > size.width) return@drawBehind
                            if (width - LiveTvGuideBlockPaddingH.toPx() * 2 < 12f) return@drawBehind
                            val dotRadius = GuideDotRadius.toPx()
                            val center = Offset(left + LiveTvGuideBlockPaddingH.toPx() + dotRadius, size.height * 0.36f)
                            val pulseValue = pulse.value
                            drawCircle(LiveTvLiveRed.copy(alpha = (1f - pulseValue) * 0.55f), dotRadius * (1f + pulseValue * 1.4f), center)
                            drawCircle(LiveTvLiveRed, dotRadius, center)
                        },
                )
            }
        }
    }
}

private val GuideDotRadius = 3.dp

private fun measureGuideText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
): TextLayoutResult = measurer.measure(
    text = text,
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = 1,
    softWrap = false,
    constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
)

@Composable
internal fun rememberLiveTvGuideTextMeasurer(): TextMeasurer = rememberTextMeasurer(cacheSize = 256)
