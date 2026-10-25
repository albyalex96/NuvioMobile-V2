package com.nuvio.app.features.player.skip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SkipInterval(
    val startTime: Double,
    val endTime: Double,
    val type: String,
    val provider: String,
)

data class NextEpisodeInfo(
    val videoId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val thumbnail: String?,
    val overview: String?,
    val released: String?,
    val hasAired: Boolean,
    val isWatched: Boolean,
    val unairedMessage: String?,
)

enum class NextEpisodeThresholdMode {
    PERCENTAGE,
    MINUTES_BEFORE_END,
}

// --- IntroDb API response models ---

@Serializable
data class IntroDbSegmentsResponse(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("season") val season: Int? = null,
    @SerialName("episode") val episode: Int? = null,
    @SerialName("intro") val intro: IntroDbSegment? = null,
    @SerialName("recap") val recap: IntroDbSegment? = null,
    @SerialName("outro") val outro: IntroDbSegment? = null,
    @SerialName("post_credits") val postCredits: IntroDbSegment? = null,
)

internal fun IntroDbSegmentsResponse.movieSkipIntervals(): List<SkipInterval> {
    val credits = outro.movieIntervalOrNull("movie-credits")
    val scene = postCredits.movieIntervalOrNull("post-credits")
    // End-credit skipping must stop before the post-credits scene, even if data overlaps.
    val safeCredits = if (credits != null && scene != null &&
        scene.startTime < credits.endTime && scene.endTime > credits.startTime
    ) {
        credits.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
    } else credits
    return listOfNotNull(safeCredits, scene)
}

private fun IntroDbSegment?.movieIntervalOrNull(type: String): SkipInterval? {
    if (this == null) return null
    val start = startSec ?: startMs?.let { it / 1000.0 } ?: return null
    val end = endSec ?: endMs?.let { it / 1000.0 } ?: return null
    if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
    return SkipInterval(start, end, type, "introdb")
}

@Serializable
data class IntroDbSegment(
    @SerialName("start_sec") val startSec: Double? = null,
    @SerialName("end_sec") val endSec: Double? = null,
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("submission_count") val submissionCount: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SubmitIntroRequest(
    @SerialName("imdb_id") val imdbId: String,
    @SerialName("season") val season: Int,
    @SerialName("episode") val episode: Int,
    @SerialName("start_sec") val startSec: Double,
    @SerialName("end_sec") val endSec: Double,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    @SerialName("segment_type") val segmentType: String,
)

// --- AniSkip API response models ---

@Serializable
data class AniSkipResponse(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipResult>? = null,
)

@Serializable
data class AniSkipResult(
    @SerialName("interval") val interval: AniSkipInterval,
    @SerialName("skipType") val skipType: String,
    @SerialName("skipId") val skipId: String? = null,
)

@Serializable
data class AniSkipInterval(
    @SerialName("startTime") val startTime: Double,
    @SerialName("endTime") val endTime: Double,
)

// --- Anime-Skip GraphQL API response models ---

@Serializable
data class AnimeSkipGraphqlResponse(
    @SerialName("data") val data: AnimeSkipData? = null,
)

@Serializable
data class AnimeSkipData(
    @SerialName("findShowsByExternalId") val findShowsByExternalId: List<AnimeSkipShow>? = null,
    @SerialName("findEpisodesByShowId") val findEpisodesByShowId: List<AnimeSkipEpisode>? = null,
)

@Serializable
data class AnimeSkipShow(
    @SerialName("id") val id: String,
)

@Serializable
data class AnimeSkipEpisode(
    @SerialName("season") val season: String? = null,
    @SerialName("number") val number: String? = null,
    @SerialName("timestamps") val timestamps: List<AnimeSkipTimestamp>? = null,
)

@Serializable
data class AnimeSkipTimestamp(
    @SerialName("at") val at: Double,
    @SerialName("type") val type: AnimeSkipTimestampType,
)

@Serializable
data class AnimeSkipTimestampType(
    @SerialName("name") val name: String,
)

// --- SkipDB API models ---

@Serializable
data class SkipDbSegmentsResponse(
    @SerialName("segments") val segments: SkipDbSegments? = null,
)

@Serializable
data class SkipDbSegments(
    @SerialName("intro") val intro: SkipDbSegment? = null,
    @SerialName("recap") val recap: SkipDbSegment? = null,
    @SerialName("outro") val outro: SkipDbSegment? = null,
    @SerialName("preview") val preview: SkipDbSegment? = null,
)

@Serializable
data class SkipDbSegment(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    /** `exact`, `shifted`, `agnostic` (no duration sent) or `out-of-range`. */
    @SerialName("match") val match: String? = null,
    @SerialName("confidence") val confidence: Double? = null,
)

@Serializable
data class SkipDbSubmitRequest(
    @SerialName("imdb_id") val imdbId: String,
    @SerialName("segment_type") val segmentType: String,
    @SerialName("season") val season: Int,
    @SerialName("episode") val episode: Int,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

@Serializable
data class SkipDbSubmitResponse(
    @SerialName("status") val status: String? = null,
)

internal const val SkipDbOutOfRangeMatch = "out-of-range"

/**
 * Converts a SkipDB answer into skip intervals, applying the user's filters. Outros without an end
 * run to the end of the stream. Previews are ignored: the player has no action for them.
 */
internal fun SkipDbSegmentsResponse.toSkipIntervals(
    settings: SkipDbSettings,
    durationSec: Double?,
    isMovie: Boolean,
): List<SkipInterval> {
    val segments = segments ?: return emptyList()
    return listOfNotNull(
        segments.intro.toSkipIntervalOrNull("intro", settings, durationSec),
        segments.recap.toSkipIntervalOrNull("recap", settings, durationSec),
        segments.outro.toSkipIntervalOrNull(if (isMovie) "movie-credits" else "outro", settings, durationSec),
    )
}

private fun SkipDbSegment?.toSkipIntervalOrNull(
    type: String,
    settings: SkipDbSettings,
    durationSec: Double?,
): SkipInterval? {
    if (this == null) return null
    if (settings.ignoreOutOfRange && match.equals(SkipDbOutOfRangeMatch, ignoreCase = true)) return null
    if (confidence != null && confidence * 100 < settings.minConfidencePercent) return null
    val start = startMs?.let { it / 1000.0 } ?: return null
    val end = endMs?.let { it / 1000.0 }
        ?: if (type == "outro" || type == "movie-credits") durationSec else null
    if (end == null || !start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
    return SkipInterval(startTime = start, endTime = end, type = type, provider = "skipdb")
}
