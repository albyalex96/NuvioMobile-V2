package com.nuvio.app.features.player.skip

import com.nuvio.app.features.settings.IntegrationLogo
import kotlinx.coroutines.Deferred

/** Episode in IMDB/TVDB numbering, the key IntroDB and SkipDB are indexed by. */
internal data class ImdbEpisodeRef(
    val imdbId: String,
    val season: Int,
    val episode: Int,
)

/** Episode number inside a single anime entry, with the IDs anime databases are indexed by. */
internal data class AnimeEpisodeRef(
    val malId: String?,
    val anilistId: String?,
    val episode: Int,
)

/**
 * What a provider can look up for one episode. The IDs are resolved once per lookup and shared by
 * every provider; each provider awaits only the parts it needs, so IMDB-keyed providers do not wait
 * for the anime ID resolution.
 */
internal class TimestampEpisodeQuery(
    val key: String,
    private val imdb: Deferred<ImdbEpisodeRef?>,
    private val anime: Deferred<AnimeEpisodeRef?>,
    private val duration: suspend () -> Double?,
) {
    suspend fun imdbEpisode(): ImdbEpisodeRef? = imdb.await()
    suspend fun animeEpisode(): AnimeEpisodeRef? = anime.await()

    /** Length of the stream being played in seconds; may suspend until the player knows it. */
    suspend fun durationSec(): Double? = duration()
}

internal class TimestampMovieQuery(
    val imdbId: String,
    private val duration: suspend () -> Double?,
) {
    suspend fun durationSec(): Double? = duration()
}

/** A timestamp entered in the player's submit dialog. */
internal data class TimestampSubmission(
    val imdbId: String,
    val season: Int,
    val episode: Int,
    val startSec: Double,
    val endSec: Double,
    /** `intro`, `recap` or `outro`. */
    val segmentType: String,
    val durationSec: Double?,
)

internal val NoTimestampDuration: suspend () -> Double? = { null }

/**
 * A skip-timestamp service. To add a new integration implement this interface (plus
 * [TimestampSubmitter] if the service accepts submissions) and register it in
 * [TimestampProviders.all]: the priority and submission selectors, the merge and the profile sync
 * pick it up from there.
 */
internal interface TimestampProvider {
    /** Stable identifier, saved in the priority order and in the submission targets. */
    val id: String
    val displayName: String
    val logo: IntegrationLogo?

    /** Non-null when the service accepts community submissions. */
    val submitter: TimestampSubmitter?
        get() = null

    /** Whether the service is queried at all: switched on and configured. Read at lookup time. */
    fun isLookupActive(): Boolean

    /** False when results depend on the stream (e.g. its duration) and must not be cached per episode. */
    fun isCacheable(): Boolean = true

    suspend fun episodeIntervals(query: TimestampEpisodeQuery): List<SkipInterval>

    suspend fun movieIntervals(query: TimestampMovieQuery): List<SkipInterval> = emptyList()

    fun clearCache() {}
}

internal interface TimestampSubmitter {
    /** Submission switched on and credentials present. */
    fun isReady(): Boolean

    suspend fun submit(submission: TimestampSubmission): Boolean
}

internal object TimestampProviders {
    /** Every integration, in the default priority order. */
    val all: List<TimestampProvider> = listOf(
        IntroDbTimestampProvider,
        SkipDbTimestampProvider,
        AnimeSkipTimestampProvider,
        AniSkipTimestampProvider,
    )

    val ids: List<String>
        get() = all.map { it.id }

    fun byId(id: String): TimestampProvider? = all.firstOrNull { it.id == id }

    /** Providers in the user's priority order. */
    fun ordered(
        priority: List<String> = TimestampProviderSettingsRepository.current().priority,
    ): List<TimestampProvider> = orderByPriority(all, priority) { it.id }

    /** Providers that accept submissions, in priority order, whether configured or not. */
    fun submitCapable(
        priority: List<String> = TimestampProviderSettingsRepository.current().priority,
    ): List<TimestampProvider> = ordered(priority).filter { it.submitter != null }

    /** Providers a timestamp entered in the player is sent to: the chosen ones that are ready. */
    fun activeSubmitTargets(
        settings: TimestampProviderSettings = TimestampProviderSettingsRepository.current(),
    ): List<TimestampProvider> {
        val ready = submitCapable(settings.priority).filter { it.submitter?.isReady() == true }
        return selectSubmitTargets(ready.map { it.id }, settings.submitTargets).mapNotNull(::byId)
    }
}

/**
 * Sorts [items] by the saved [priority]; items missing from it (e.g. integrations added later)
 * follow in their original order, and unknown saved IDs are ignored.
 */
internal fun <T> orderByPriority(items: List<T>, priority: List<String>, id: (T) -> String): List<T> {
    val rank = priority.withIndex().associate { (index, value) -> value to index }
    return items.withIndex()
        .sortedWith(compareBy({ rank[id(it.value)] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/**
 * The submission targets among the [readyIds] (already in priority order): the user's choice, or the
 * highest-priority ready service while nothing has been chosen yet.
 */
internal fun selectSubmitTargets(readyIds: List<String>, chosen: List<String>?): List<String> =
    if (chosen == null) readyIds.take(1) else readyIds.filter { it in chosen }

/**
 * Merges the results of several providers, given in priority order. With [mergeProviders], for each
 * kind of segment (opening, recap, ending, post-credits scene) the first provider that has it wins, so
 * a lower provider only fills the kinds the higher ones are missing; without it, only the first
 * provider that has any usable segment is used.
 */
internal fun mergeTimestampsByPriority(
    resultsInPriorityOrder: List<List<SkipInterval>>,
    mergeProviders: Boolean = true,
): List<SkipInterval> {
    val sources = if (mergeProviders) {
        resultsInPriorityOrder
    } else {
        listOfNotNull(resultsInPriorityOrder.firstOrNull { result -> result.any { skipSegmentCategory(it.type) != null } })
    }
    val chosen = LinkedHashMap<String, SkipInterval>()
    for (result in sources) {
        for (interval in result) {
            val category = skipSegmentCategory(interval.type) ?: continue
            if (category !in chosen) chosen[category] = interval
        }
    }
    return chosen.values.toList()
}

internal fun skipSegmentCategory(type: String): String? = when (type.lowercase()) {
    "intro", "op", "mixed-op" -> "opening"
    "outro", "ed", "mixed-ed", "credits", "ending", "movie-credits" -> "ending"
    "recap" -> "recap"
    "post-credits" -> "post-credits"
    else -> null
}

/** End-credit skipping must stop before the post-credits scene, even if the data overlaps. */
internal fun trimMovieCreditsBeforePostCredits(intervals: List<SkipInterval>): List<SkipInterval> {
    val credits = intervals.firstOrNull { it.type == "movie-credits" } ?: return intervals
    val scene = intervals.firstOrNull { it.type == "post-credits" } ?: return intervals
    if (scene.startTime >= credits.endTime || scene.endTime <= credits.startTime) return intervals
    val trimmed = credits.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
    return intervals.mapNotNull { if (it === credits) trimmed else it }
}
