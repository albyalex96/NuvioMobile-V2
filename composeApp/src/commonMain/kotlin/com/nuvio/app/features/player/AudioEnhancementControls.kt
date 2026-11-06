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
import nuvio.composeapp.generated.resources.compose_player_audio_enhancement_off
import nuvio.composeapp.generated.resources.compose_player_dialogue_boost
import nuvio.composeapp.generated.resources.compose_player_dialogue_boost_hint
import nuvio.composeapp.generated.resources.compose_player_dynamic_compression
import nuvio.composeapp.generated.resources.compose_player_dynamic_compression_hint
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Dialogue boost + dynamic range compression sliders of the Audio panel. */
@Composable
internal fun AudioEnhancementControls(
    dialogueBoostPercent: Int,
    compressionPercent: Int,
    onDialogueBoostChanged: (Int) -> Unit,
    onCompressionChanged: (Int) -> Unit,
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
    }
}

/**
 * Slider usable with the remote (− / + buttons, hold to repeat) and with touch or mouse
 * (tap or drag on the bar). The bar itself is not focusable, so D-pad focus stays on the buttons.
 */
@Composable
private fun AudioPercentSlider(
    title: String,
    hint: String,
    percent: Int,
    onChanged: (Int) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val current = normalizeAudioEnhancementPercent(percent)
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
                text = if (current == 0) offLabel else "$current%",
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
                enabled = current > AUDIO_ENHANCEMENT_MIN_PERCENT,
                stepForRepeat = { AUDIO_ENHANCEMENT_STEP_PERCENT },
                onStep = { step -> latestOnChanged(normalizeAudioEnhancementPercent(latestPercent - step)) },
                size = 40.dp,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            latestOnChanged(percentAt(offset.x, size.width.toFloat()))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            latestOnChanged(percentAt(change.position.x, size.width.toFloat()))
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
                            .fillMaxWidth(current / 100f)
                            .background(tokens.colors.accent),
                    )
                }
            }
            HoldRepeatStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.compose_player_audio_enhancement_increase, title),
                enabled = current < AUDIO_ENHANCEMENT_MAX_PERCENT,
                stepForRepeat = { AUDIO_ENHANCEMENT_STEP_PERCENT },
                onStep = { step -> latestOnChanged(normalizeAudioEnhancementPercent(latestPercent + step)) },
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

private fun percentAt(x: Float, width: Float): Int {
    if (width <= 0f) return 0
    val raw = (x / width).coerceIn(0f, 1f) * 100f
    val snapped = (raw / AUDIO_ENHANCEMENT_STEP_PERCENT).roundToInt() * AUDIO_ENHANCEMENT_STEP_PERCENT
    return normalizeAudioEnhancementPercent(snapped)
}
