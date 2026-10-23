package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_audio_delay
import nuvio.composeapp.generated.resources.compose_player_audio_delay_decrease
import nuvio.composeapp.generated.resources.compose_player_audio_delay_from_stream
import nuvio.composeapp.generated.resources.compose_player_audio_delay_hint
import nuvio.composeapp.generated.resources.compose_player_audio_delay_increase
import nuvio.composeapp.generated.resources.compose_player_audio_delay_saved_for_device
import nuvio.composeapp.generated.resources.compose_player_audio_delay_session_only
import nuvio.composeapp.generated.resources.compose_player_reset
import org.jetbrains.compose.resources.stringResource

/** Step for the n-th repeat while − / + is held: fine at first, then faster. */
internal fun audioDelayRepeatStepMs(repeat: Int): Int = when {
    repeat < 10 -> AUDIO_DELAY_STEP_MS
    repeat < 25 -> 100
    else -> 250
}

/**
 * Audio delay section of the Audio panel. [audioDelayMs] is the total delay applied to the
 * player; [streamDelayMs] is the part declared by the stream; [savedForDeviceLabel] names the
 * audio output the value is saved for (null = not saved).
 */
@Composable
internal fun AudioDelayControls(
    audioDelayMs: Int,
    streamDelayMs: Int,
    savedForDeviceLabel: String?,
    onAudioDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDelayMs = audioDelayMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
    val latestDelayMs by rememberUpdatedState(currentDelayMs)
    val latestOnChanged by rememberUpdatedState(onAudioDelayChanged)
    val secondaryColor = Color.White.copy(alpha = 0.72f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.compose_player_audio_delay),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldRepeatStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(Res.string.compose_player_audio_delay_decrease),
                enabled = currentDelayMs > AUDIO_DELAY_MIN_MS,
                stepForRepeat = ::audioDelayRepeatStepMs,
                onStep = { stepMs -> latestOnChanged(latestDelayMs - stepMs) },
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatAudioDelay(currentDelayMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HoldRepeatStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.compose_player_audio_delay_increase),
                enabled = currentDelayMs < AUDIO_DELAY_MAX_MS,
                stepForRepeat = ::audioDelayRepeatStepMs,
                onStep = { stepMs -> latestOnChanged(latestDelayMs + stepMs) },
            )
            AudioDelayResetAction(
                label = stringResource(Res.string.compose_player_reset),
                // With a stream delay, "reset" goes back to what the stream declared.
                enabled = currentDelayMs != streamDelayMs,
                onClick = { onAudioDelayChanged(streamDelayMs) },
            )
        }
        val statusText = when {
            streamDelayMs != 0 -> stringResource(
                Res.string.compose_player_audio_delay_from_stream,
                formatAudioDelay(streamDelayMs),
            )
            savedForDeviceLabel != null -> stringResource(
                Res.string.compose_player_audio_delay_saved_for_device,
                savedForDeviceLabel,
            )
            else -> stringResource(Res.string.compose_player_audio_delay_session_only)
        }
        Text(
            text = statusText,
            color = secondaryColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(Res.string.compose_player_audio_delay_hint),
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AudioDelayResetAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else tokens.opacity.disabled)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            // Stays focusable when dimmed: on TV a control that turns disabled under the focus
            // would leave the D-pad with nothing focused.
            .clickable(onClick = { if (enabled) onClick() })
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
