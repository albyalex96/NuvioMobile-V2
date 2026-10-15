package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.coroutines.delay

private const val STILL_WATCHING_NIGHT_START_HOUR = 22
private const val STILL_WATCHING_NIGHT_END_HOUR = 4
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val LIVE_TV_STILL_WATCHING_TICK_MS = 1_000L

private val stillWatchingLog = Logger.withTag("StillWatching")

/** Seconds the prompt waits for a choice before closing the player. */
internal const val STILL_WATCHING_AUTO_EXIT_SECONDS = 10

/** Night window is 22:00 (inclusive) to 04:00 (exclusive), local time. */
internal fun isStillWatchingNightHour(hour: Int): Boolean =
    hour >= STILL_WATCHING_NIGHT_START_HOUR || hour < STILL_WATCHING_NIGHT_END_HOUR

internal fun shouldPromptStillWatching(
    settings: PlayerSettingsUiState,
    consecutiveEpisodesPlayed: Int,
    localHour: () -> Int?,
): Boolean {
    if (!settings.stillWatchingPromptEnabled) return false
    if (consecutiveEpisodesPlayed < settings.stillWatchingEpisodeCount) return false
    if (!settings.stillWatchingNightOnly) return true
    return localHour()?.let(::isStillWatchingNightHour) ?: false
}

/** Live TV variant: asks after the configured minutes of continuous playback. */
internal fun shouldPromptStillWatchingLiveTv(
    settings: PlayerSettingsUiState,
    watchedMs: Long,
): Boolean = settings.stillWatchingLiveTvEnabled &&
    watchedMs >= settings.stillWatchingLiveTvMinutes * MILLIS_PER_MINUTE

private fun currentLocalHour(): Int? {
    val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
    val today = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowMs) ?: return null
    val midnightMs = EpisodeReleaseDatePlatform.localDateTimeToEpochMs("${today}T00:00:00.000") ?: return null
    return ((nowMs - midnightMs) / MILLIS_PER_HOUR).toInt().coerceIn(0, 23)
}

internal fun PlayerScreenRuntime.shouldPromptStillWatching(): Boolean =
    shouldPromptStillWatching(
        settings = playerSettingsUiState,
        consecutiveEpisodesPlayed = consecutiveEpisodesPlayed,
        localHour = ::currentLocalHour,
    )

/** Automatic advances extend the streak; any manual choice starts a new one. */
internal fun PlayerScreenRuntime.recordEpisodeAdvance(automatic: Boolean) {
    consecutiveEpisodesPlayed = if (automatic) consecutiveEpisodesPlayed + 1 else 1
}

internal fun PlayerScreenRuntime.showStillWatchingPromptAndPause() {
    cancelNextEpisodeAutoPlay()
    shouldPlay = false
    playerController?.pause()
    showStillWatchingPrompt = true
}

internal fun PlayerScreenRuntime.continueAfterStillWatchingPrompt() {
    showStillWatchingPrompt = false
    if (isLiveTvPlayback) {
        stillWatchingLog.i { "Live TV prompt confirmed, resuming the channel" }
        resetLiveTvStillWatchingTimer()
        shouldPlay = true
        playerController?.play()
        return
    }
    consecutiveEpisodesPlayed = 1
    playNextEpisode()
}

/** Zapping or answering the prompt shows the viewer is present, so the count starts over. */
internal fun PlayerScreenRuntime.resetLiveTvStillWatchingTimer() {
    liveTvWatchedMsSincePresence = 0L
}

/**
 * Counts live TV time that is actually playing and pauses with the prompt once the configured
 * minutes are reached. Paused, buffering or prompt time is not counted.
 */
@Composable
internal fun PlayerScreenRuntime.BindLiveTvStillWatchingTimer() {
    val enabled = isLiveTvPlayback && playerSettingsUiState.stillWatchingLiveTvEnabled
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        resetLiveTvStillWatchingTimer()
        while (true) {
            delay(LIVE_TV_STILL_WATCHING_TICK_MS)
            if (showStillWatchingPrompt || !playbackSnapshot.isPlaying) continue
            liveTvWatchedMsSincePresence += LIVE_TV_STILL_WATCHING_TICK_MS
            if (shouldPromptStillWatchingLiveTv(playerSettingsUiState, liveTvWatchedMsSincePresence)) {
                stillWatchingLog.i {
                    "Live TV watched for ${playerSettingsUiState.stillWatchingLiveTvMinutes} min, asking if still watching"
                }
                showStillWatchingPromptAndPause()
            }
        }
    }
}

internal fun PlayerScreenRuntime.exitAfterStillWatchingPrompt() {
    showStillWatchingPrompt = false
    flushWatchProgress()
    args.onBack()
}
