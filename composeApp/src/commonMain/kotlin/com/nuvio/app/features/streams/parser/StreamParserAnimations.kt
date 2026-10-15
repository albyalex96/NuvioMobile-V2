package com.nuvio.app.features.streams.parser

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

// Slow sweep (1.9 s) with a short pause (0.5 s): a calm glint that comes back often.
private const val SHIMMER_CYCLE_MS = 2400
private const val SHIMMER_SWEEP_MS = 1900
private const val BLINK_MS = 850
private const val MAGNET_CYCLE_MS = 1400
private const val PROXY_WAVE_MS = 1800

/** Constant state used when animations are disabled, so callers read values the same way. */
@Composable
private fun staticFloat(value: Float): State<Float> = remember(value) { mutableFloatStateOf(value) }

/** 0→1 progress of the oblique light band; stays at 1 (off-badge) during the pause. */
@Composable
internal fun rememberShimmerProgress(enabled: Boolean): State<Float> {
    if (!enabled) return staticFloat(1f)
    val transition = rememberInfiniteTransition(label = "streamParserShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = SHIMMER_CYCLE_MS
                0f at 0 using LinearEasing
                1f at SHIMMER_SWEEP_MS
                1f at SHIMMER_CYCLE_MS
            },
        ),
        label = "streamParserShimmerProgress",
    )
}

/**
 * Draws an oblique, semi-transparent light band sweeping left → right over the content,
 * giving the "precious foil" glint. [progress] comes from [rememberShimmerProgress].
 */
internal fun Modifier.shimmerSweep(progress: State<Float>, highlight: Color, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithContent {
            drawContent()
            val band = size.height * 1.8f
            val travel = size.width + band * 2f
            val center = -band + travel * progress.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(center - band, 0f),
                    end = Offset(center, size.height),
                ),
            )
        }
    }

/** Alpha pulse 1 → [minAlpha] used by the cached badge blink. */
@Composable
internal fun rememberBlinkAlpha(enabled: Boolean, minAlpha: Float = 0.35f): State<Float> {
    if (!enabled) return staticFloat(1f)
    val transition = rememberInfiniteTransition(label = "streamParserBlink")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BLINK_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamParserBlinkAlpha",
    )
}

/** Magnet sway in degrees (−12…12) for the torrent badge. */
@Composable
internal fun rememberMagnetSway(enabled: Boolean): State<Float> {
    if (!enabled) return staticFloat(0f)
    val transition = rememberInfiniteTransition(label = "streamParserMagnet")
    return transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MAGNET_CYCLE_MS / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamParserMagnetSway",
    )
}

/** Linear 0→1 loop shared by the attracted particles and the proxy wave. */
@Composable
internal fun rememberLoopProgress(enabled: Boolean, durationMs: Int = MAGNET_CYCLE_MS): State<Float> {
    if (!enabled) return staticFloat(0f)
    val transition = rememberInfiniteTransition(label = "streamParserLoop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = durationMs, easing = LinearEasing)),
        label = "streamParserLoopProgress",
    )
}

@Composable
internal fun rememberProxyWaveProgress(enabled: Boolean): State<Float> =
    rememberLoopProgress(enabled, PROXY_WAVE_MS)

/**
 * Three particles that drift from the right edge towards the magnet (on the left),
 * fading in as they get closer: peers being "attracted" by the torrent.
 */
internal fun Modifier.attractedParticles(progress: State<Float>, color: Color, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawBehind {
            val radius = size.height * 0.07f
            repeat(3) { index ->
                val phase = (progress.value + index / 3f) % 1f
                val x = size.width * (1f - phase)
                val y = size.height * (0.3f + 0.2f * index)
                drawCircle(color = color.copy(alpha = color.alpha * phase), radius = radius, center = Offset(x, y))
            }
        }
    }

/** Expanding, fading ring behind the proxy shield icon. */
internal fun Modifier.proxyWave(progress: State<Float>, color: Color, enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawBehind {
            val maxRadius = size.minDimension * 0.9f
            val value = progress.value
            drawCircle(
                color = color.copy(alpha = (1f - value) * 0.8f),
                radius = maxRadius * (0.35f + 0.65f * value),
                style = Stroke(width = size.minDimension * 0.08f),
            )
        }
    }
