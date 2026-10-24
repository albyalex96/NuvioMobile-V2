package com.nuvio.app.features.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import com.nuvio.app.core.ui.nuvioTypeScale
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val MILLIS_PER_MINUTE = 60_000L

/** Current local time in the player controls; refreshed at every minute change. */
@Composable
internal fun PlayerClock(
    use24Hour: Boolean,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_MINUTE - nowMs % MILLIS_PER_MINUTE)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }
    val time = remember(nowMs, use24Hour) {
        val local = kotlin.time.Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(TimeZone.currentSystemDefault())
        formatPlayerClockTime(local.hour, local.minute, use24Hour)
    }
    Text(
        text = time,
        style = MaterialTheme.nuvioTypeScale.titleLg.copy(
            fontSize = metrics.episodeInfoSize * 1.25f,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 1f), blurRadius = 4f),
        ),
        color = Color.White,
        maxLines = 1,
        modifier = modifier,
    )
}
