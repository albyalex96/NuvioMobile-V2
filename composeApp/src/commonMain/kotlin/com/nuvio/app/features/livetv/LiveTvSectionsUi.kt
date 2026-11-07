package com.nuvio.app.features.livetv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_section_channels
import nuvio.composeapp.generated.resources.live_tv_section_guide
import nuvio.composeapp.generated.resources.live_tv_section_on_demand
import nuvio.composeapp.generated.resources.live_tv_section_sports
import nuvio.composeapp.generated.resources.live_tv_sports_live_badge
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Red used for every "live" marker in the Live TV sections (badge, pulsing dots, glow). */
internal val LiveTvLiveRed = Color(0xFFE53935)

internal enum class LiveTvSection(val label: StringResource, val icon: ImageVector) {
    Channels(Res.string.live_tv_section_channels, Icons.Rounded.LiveTv),
    Guide(Res.string.live_tv_section_guide, Icons.Rounded.CalendarViewWeek),
    Sports(Res.string.live_tv_section_sports, Icons.Rounded.SportsSoccer),
    OnDemand(Res.string.live_tv_section_on_demand, Icons.Rounded.Movie),
}

/**
 * Section switcher: pill tabs with an icon. The selected one grows into an accent pill
 * (animated colors and a springy icon); the Sports tab gets a pulsing red dot while a match is live.
 */
@Composable
internal fun LiveTvSectionTabs(
    selected: LiveTvSection,
    onSelected: (LiveTvSection) -> Unit,
    sportsLive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvSection.entries.forEach { section ->
            LiveTvSectionTab(
                section = section,
                selected = section == selected,
                showLiveDot = section == LiveTvSection.Sports && sportsLive,
                onClick = { onSelected(section) },
            )
        }
    }
}

@Composable
private fun LiveTvSectionTab(
    section: LiveTvSection,
    selected: Boolean,
    showLiveDot: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val background by animateColorAsState(
        targetValue = if (selected) tokens.colors.accent else tokens.colors.surface,
        animationSpec = tween(220),
        label = "liveTvTabBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) tokens.colors.onAccent else tokens.colors.textSecondary,
        animationSpec = tween(220),
        label = "liveTvTabContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "liveTvTabIcon",
    )
    val shape = tokens.shapes.chip
    Row(
        modifier = Modifier
            .liveTvPressScale(onClick)
            .clip(shape)
            .background(background)
            .then(
                if (selected) Modifier else Modifier.border(NuvioTokens.Border.thin, tokens.colors.borderSubtle, shape),
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = stringResource(section.label),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        if (showLiveDot) {
            LiveTvPulsingDot(size = 7.dp, color = if (selected) tokens.colors.onAccent else LiveTvLiveRed)
        }
    }
}

/** Shrinks slightly while pressed and handles the click: a tactile feel for cards and tabs. */
@Composable
internal fun Modifier.liveTvPressScale(onClick: () -> Unit, enabled: Boolean = true): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "liveTvPressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            // Default indication: on the TV app it is the focus ring of TvThemeOverlay.
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/** A dot that keeps pulsing: a solid core plus a ring that expands and fades ("on air" signal). */
@Composable
internal fun LiveTvPulsingDot(
    size: Dp = 8.dp,
    color: Color = LiveTvLiveRed,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "liveTvPulse")
    // Read only inside drawBehind: the animation redraws the dot without recomposing the caller.
    val progressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "liveTvPulseRing",
    )
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val progress = progressState.value
                val radius = this.size.minDimension / 2f
                drawCircle(
                    color = color.copy(alpha = (1f - progress) * 0.55f),
                    radius = radius * (1f + progress * 1.4f),
                )
                drawCircle(color = color, radius = radius)
            },
    )
}

/**
 * Alpha that blinks between 1 and [minAlpha]: the classic "LIVE" blinking effect. Returned as a
 * State so callers read it in graphicsLayer/draw lambdas, never in composition (that would
 * recompose the whole card on every frame).
 */
@Composable
internal fun rememberLiveTvBlinkAlpha(minAlpha: Float = 0.35f, periodMillis: Int = 900): State<Float> {
    val transition = rememberInfiniteTransition(label = "liveTvBlink")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveTvBlinkAlpha",
    )
}

/** Outline of [shape] whose alpha follows [alpha], drawn in the draw phase only. */
internal fun Modifier.liveTvGlowBorder(
    alpha: State<Float>,
    shape: Shape,
    color: Color = LiveTvLiveRed,
    width: Dp = 1.5.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokePx = width.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline = outline, color = color.copy(alpha = alpha.value), style = Stroke(width = strokePx))
}

/** Red "LIVE" pill with a blinking dot. */
@Composable
internal fun LiveTvLiveBadge(modifier: Modifier = Modifier) {
    val blink = rememberLiveTvBlinkAlpha()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LiveTvLiveRed)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = blink.value }
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            text = stringResource(Res.string.live_tv_sports_live_badge),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
