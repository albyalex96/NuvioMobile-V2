package com.nuvio.app.features.player

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform

private const val STILL_WATCHING_NIGHT_START_HOUR = 22
private const val STILL_WATCHING_NIGHT_END_HOUR = 4
private const val MILLIS_PER_HOUR = 3_600_000L

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
    consecutiveEpisodesPlayed = 1
    playNextEpisode()
}

internal fun PlayerScreenRuntime.exitAfterStillWatchingPrompt() {
    showStillWatchingPrompt = false
    flushWatchProgress()
    args.onBack()
}
