package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_audio_enhancement_decrease
import nuvio.composeapp.generated.resources.compose_player_audio_enhancement_increase
import nuvio.composeapp.generated.resources.compose_player_audio_amplification
import nuvio.composeapp.generated.resources.compose_player_audio_amplification_hint
import nuvio.composeapp.generated.resources.compose_player_audio_enhancement_off
import nuvio.composeapp.generated.resources.compose_player_dialogue_boost
import nuvio.composeapp.generated.resources.compose_player_dialogue_boost_hint
import nuvio.composeapp.generated.resources.compose_player_dynamic_compression
import nuvio.composeapp.generated.resources.compose_player_dynamic_compression_hint
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Dialogue boost, dynamic range compression and amplification sliders of the Audio panel. */
@Composable
internal fun AudioEnhancementControls(
    dialogueBoostPercent: Int,
    compressionPercent: Int,
    onDialogueBoostChanged: (Int) -> Unit,
    onCompressionChanged: (Int) -> Unit,
    amplificationDb: Int,
    onAmplificationChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AudioPercentSlider(
            title = stringResource(Res.string.compose_player_dialogue_boost),
            hint = stringResource(Res.string.compose_player_dialogue_boost_hint),
            percent = dialogueBoostPercent,
            onChanged = onDialogueBoostChanged,
        )
        AudioPercentSlider(
            title = stringResource(Res.string.compose_player_dynamic_compression),
            hint = stringResource(Res.string.compose_player_dynamic_compression_hint),
            percent = compressionPercent,
            onChanged = onCompressionChanged,
        )
        AudioStepSlider(
            title = stringResource(Res.string.compose_player_audio_amplification),
            hint = stringResource(Res.string.compose_player_audio_amplification_hint),
            value = amplificationDb,
            min = AUDIO_AMPLIFICATION_MIN_DB,
            max = AUDIO_AMPLIFICATION_MAX_DB,
            step = AUDIO_AMPLIFICATION_STEP_DB,
            valueText = { "+$it dB" },
            onChanged = onAmplificationChanged,
        )
    }
}

@Composable
private fun AudioPercentSlider(
    title: String,
    hint: String,
    percent: Int,
    onChanged: (Int) -> Unit,
) = AudioStepSlider(
    title = title,
    hint = hint,
    value = percent,
    min = AUDIO_ENHANCEMENT_MIN_PERCENT,
    max = AUDIO_ENHANCEMENT_MAX_PERCENT,
    step = AUDIO_ENHANCEMENT_STEP_PERCENT,
    valueText = { "$it%" },
    onChanged = onChanged,
)

/**
 * Slider usable with the remote (− / + buttons, hold to repeat) and with touch or mouse
 * (tap or drag on the bar). The bar itself is not focusable, so D-pad focus stays on the buttons.
 */
@Composable
private fun AudioStepSlider(
    title: String,
    hint: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    valueText: (Int) -> String,
    onChanged: (Int) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val clamp = { v: Int -> v.coerceIn(min, max) }
    val current = clamp(value)
    val latestPercent by rememberUpdatedState(current)
    val latestOnChanged by rememberUpdatedState(onChanged)
    val offLabel = stringResource(Res.string.compose_player_audio_enhancement_off)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (current == 0) offLabel else valueText(current),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldRepeatStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(Res.string.compose_player_audio_enhancement_decrease, title),
                enabled = current > min,
                stepForRepeat = { step },
                onStep = { delta -> latestOnChanged(clamp(latestPercent - delta)) },
                size = 40.dp,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            latestOnChanged(valueAt(offset.x, size.width.toFloat(), min, max, step))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            latestOnChanged(valueAt(change.position.x, size.width.toFloat(), min, max, step))
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(if (max > min) (current - min).toFloat() / (max - min) else 0f)
                            .background(tokens.colors.accent),
                    )
                }
            }
            HoldRepeatStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.compose_player_audio_enhancement_increase, title),
                enabled = current < max,
                stepForRepeat = { step },
                onStep = { delta -> latestOnChanged(clamp(latestPercent + delta)) },
                size = 40.dp,
            )
        }
        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun valueAt(x: Float, width: Float, min: Int, max: Int, step: Int): Int {
    if (width <= 0f) return min
    val raw = min + (x / width).coerceIn(0f, 1f) * (max - min)
    val snapped = min + ((raw - min) / step).roundToInt() * step
    return snapped.coerceIn(min, max)
}
