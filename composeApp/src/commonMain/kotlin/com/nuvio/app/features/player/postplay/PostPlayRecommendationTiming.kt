package com.nuvio.app.features.player.postplay

import com.nuvio.app.features.player.MAX_POST_PLAY_MOVIE_THRESHOLD_PERCENT
import com.nuvio.app.features.player.MIN_POST_PLAY_MOVIE_THRESHOLD_PERCENT
import com.nuvio.app.features.player.skip.NextEpisodeThresholdMode
import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.player.skip.SkipInterval

/** Content kinds that can end with post-play recommendations. */
internal enum class PostPlayContentType { MOVIE, SERIES }

/** Recommendations are prefetched at this progress (series) or this far before the end. */
internal const val POST_PLAY_PREFETCH_PROGRESS = 0.9f
internal const val POST_PLAY_PREFETCH_REMAINING_MS = 10 * 60_000L

/** Seconds of countdown before the trailer of the recommendation starts by itself. */
internal const val POST_PLAY_TRAILER_COUNTDOWN_SECONDS = 5

/** Recommendations prefetched for movies this many points before the movie threshold. */
private const val MOVIE_PREFETCH_LEAD_PERCENT = 5

/** Movie end window used when no credits are known and the episode threshold applies (ms). */
private const val END_OF_VIDEO_EPSILON_MS = 1_000L

internal fun resolvePostPlayContentType(type: String?): PostPlayContentType? =
    when (type?.trim()?.lowercase()) {
        "movie", "film" -> PostPlayContentType.MOVIE
        "series", "tv", "show", "tvshow", "anime" -> PostPlayContentType.SERIES
        else -> null
    }

/**
 * Movies always end with recommendations. A series only when no next episode can follow: the last
 * episode, or a next episode not aired yet. Nothing is decided before the episode list is known.
 */
internal fun shouldUsePostPlayRecommendation(
    contentType: PostPlayContentType?,
    episodesResolved: Boolean,
    hasNextEpisode: Boolean,
    nextEpisodeHasAired: Boolean?,
    enabled: Boolean,
): Boolean = enabled && when (contentType) {
    PostPlayContentType.MOVIE -> true
    PostPlayContentType.SERIES -> episodesResolved && (!hasNextEpisode || nextEpisodeHasAired != true)
    null -> false
}

internal fun shouldPrefetchPostPlayRecommendation(
    positionMs: Long,
    durationMs: Long,
    progressThreshold: Float,
): Boolean {
    if (durationMs <= 0L) return false
    val position = positionMs.coerceIn(0L, durationMs)
    val progress = position.toDouble() / durationMs.toDouble()
    return progress >= progressThreshold.coerceIn(0f, 1f) ||
        durationMs - position <= POST_PLAY_PREFETCH_REMAINING_MS
}

internal fun postPlayPrefetchProgress(
    contentType: PostPlayContentType?,
    movieThresholdPercent: Int,
    durationMs: Long,
    skipIntervals: List<SkipInterval>,
    episodeThresholdMode: NextEpisodeThresholdMode,
    episodeThresholdPercent: Float,
    episodeThresholdMinutesBeforeEnd: Float,
): Float {
    if (contentType != PostPlayContentType.MOVIE) return POST_PLAY_PREFETCH_PROGRESS
    val threshold = movieThresholdPercent.coerceIn(MIN_POST_PLAY_MOVIE_THRESHOLD_PERCENT, MAX_POST_PLAY_MOVIE_THRESHOLD_PERCENT)
    val triggerProgress = if (durationMs > 0L) {
        movieRecommendationTriggerPositionMs(
            durationMs, threshold, skipIntervals, episodeThresholdMode,
            episodeThresholdPercent, episodeThresholdMinutesBeforeEnd,
        ).toFloat() / durationMs
    } else {
        threshold / 100f
    }
    return (triggerProgress - MOVIE_PREFETCH_LEAD_PERCENT / 100f).coerceAtLeast(0f)
}

/** Movies: the movie threshold (or the credits); series: the same moment as the next-episode card. */
internal fun shouldShowPostPlayRecommendation(
    contentType: PostPlayContentType?,
    positionMs: Long,
    durationMs: Long,
    skipIntervals: List<SkipInterval>,
    movieThresholdPercent: Int,
    episodeThresholdMode: NextEpisodeThresholdMode,
    episodeThresholdPercent: Float,
    episodeThresholdMinutesBeforeEnd: Float,
): Boolean = when (contentType) {
    PostPlayContentType.MOVIE -> durationMs > 0L &&
        positionMs.coerceIn(0L, durationMs) >= movieRecommendationTriggerPositionMs(
            durationMs, movieThresholdPercent, skipIntervals, episodeThresholdMode,
            episodeThresholdPercent, episodeThresholdMinutesBeforeEnd,
        )
    PostPlayContentType.SERIES -> PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
        positionMs = positionMs,
        durationMs = durationMs,
        skipIntervals = skipIntervals,
        thresholdMode = episodeThresholdMode,
        thresholdPercent = episodeThresholdPercent,
        thresholdMinutesBeforeEnd = episodeThresholdMinutesBeforeEnd,
    )
    null -> false
}

/**
 * Where the movie recommendation appears: after a post-credits scene when one is known, at the start
 * of the credits, otherwise at the movie threshold percentage.
 */
internal fun movieRecommendationTriggerPositionMs(
    durationMs: Long,
    thresholdPercent: Int,
    skipIntervals: List<SkipInterval>,
    episodeThresholdMode: NextEpisodeThresholdMode,
    episodeThresholdPercent: Float,
    episodeThresholdMinutesBeforeEnd: Float,
): Long {
    val threshold = thresholdPercent.coerceIn(MIN_POST_PLAY_MOVIE_THRESHOLD_PERCENT, MAX_POST_PLAY_MOVIE_THRESHOLD_PERCENT)
    val fallbackPositionMs = kotlin.math.ceil(durationMs * (threshold / 100.0)).toLong()
    val validIntervals = skipIntervals.filter {
        it.startTime.isFinite() && it.endTime.isFinite() &&
            it.startTime >= 0.0 && it.endTime > it.startTime &&
            it.startTime * 1_000.0 < durationMs
    }
    val credits = validIntervals.filter {
        it.type == "movie-credits" && it.endTime * 1_000.0 <= durationMs + END_OF_VIDEO_EPSILON_MS
    }
    val firstCreditsStart = credits.minOfOrNull { it.startTime }
    val scenes = validIntervals.filter {
        it.type == "post-credits" && (firstCreditsStart == null || it.startTime >= firstCreditsStart)
    }
    if (scenes.isNotEmpty()) {
        // Keep a playable scene even when its submitted end exceeds this release's runtime.
        return (scenes.maxOf { it.endTime } * 1_000.0).toLong().coerceAtMost(durationMs)
    }
    if (credits.isEmpty()) return fallbackPositionMs

    val latestCreditsEndMs = (credits.maxOf { it.endTime } * 1_000.0).toLong()
    val postCreditsGapMs = durationMs - latestCreditsEndMs
    val userThresholdMs = when (episodeThresholdMode) {
        NextEpisodeThresholdMode.PERCENTAGE ->
            ((1.0 - episodeThresholdPercent.coerceIn(97f, 100f) / 100.0) * durationMs).toLong()
        NextEpisodeThresholdMode.MINUTES_BEFORE_END ->
            (episodeThresholdMinutesBeforeEnd.coerceIn(0f, 3.5f) * 60_000f).toLong()
    }
    // An unexplained tail may contain a scene: use the later episode threshold, not the movie setting.
    return if (postCreditsGapMs > userThresholdMs) {
        (durationMs - userThresholdMs).coerceAtLeast(latestCreditsEndMs)
    } else {
        (credits.minOf { it.startTime } * 1_000.0).toLong()
    }
}
