package com.nuvio.app.features.player.postplay

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.skip.NextEpisodeThresholdMode
import com.nuvio.app.features.player.skip.SkipInterval
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import com.nuvio.app.features.watching.domain.isShortPlaceholderDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = Logger.withTag("PostPlayRecommendations")

/** Recommendations kept for one title: the panel lets the user browse them. */
private const val MAX_POST_PLAY_RECOMMENDATIONS = 12

/** One suggested title of the post-play panel. */
@Immutable
internal data class PostPlayRecommendation(
    val id: String,
    val type: String,
    val title: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val ageRating: String? = null,
    val trailer: TrailerPlaybackSource? = null,
)

@Immutable
internal data class PostPlayRecommendationUiState(
    val recommendation: PostPlayRecommendation? = null,
    val index: Int = 0,
    val count: Int = 0,
    /** Title just watched, for "Because you watched X". */
    val sourceTitle: String = "",
    val isLoading: Boolean = false,
    val isVisible: Boolean = false,
    /** The user went back to the player: the panel does not come back for this playback. */
    val hasReturnedToPlayer: Boolean = false,
    val isLoadingTrailer: Boolean = false,
    val countdownSeconds: Int? = null,
    val isTrailerPlaying: Boolean = false,
    val hasAutoPlayedTrailer: Boolean = false,
) {
    val canShowPrevious: Boolean get() = index > 0
    val canShowNext: Boolean get() = index < count - 1
}

/** What the player knows at a given moment: the controller decides from it. */
internal data class PostPlayPlaybackInput(
    /** Changes with the item playing (another episode resets the panel). */
    val identity: String,
    val contentType: PostPlayContentType?,
    val enabled: Boolean,
    val movieThresholdPercent: Int,
    val episodesResolved: Boolean,
    val hasNextEpisode: Boolean,
    val nextEpisodeHasAired: Boolean?,
    val nextEpisodeAutoPlayActive: Boolean,
    val hasError: Boolean,
    /** A panel, a prompt or a timeline scrub is in progress: the panel waits for it. */
    val blocked: Boolean,
    val isEnded: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val skipIntervals: List<SkipInterval>,
    val episodeThresholdMode: NextEpisodeThresholdMode,
    val episodeThresholdPercent: Float,
    val episodeThresholdMinutesBeforeEnd: Float,
    val meta: MetaDetails?,
    val metaId: String,
    val metaType: String,
    val title: String,
)

/**
 * Post-play recommendations: near the end of a movie (or of a series with no next episode) a panel
 * suggests similar titles from the "More like this" source, with trailer, browsing, play and
 * details. Ported from Nuvio TV.
 */
internal class PostPlayRecommendationController(
    private val scopeProvider: () -> CoroutineScope,
) {
    var uiState by mutableStateOf(PostPlayRecommendationUiState())
        private set

    /** Trailers play inside the panel only where the in-app trailer player exists. */
    val trailerPlaybackSupported: Boolean =
        AppFeaturePolicy.heroTrailerPlaybackSupported &&
            AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP

    private var candidates: List<MetaPreview> = emptyList()
    private val cache = mutableMapOf<Int, PostPlayRecommendation>()
    private val detailJobs = mutableMapOf<Int, Job>()
    private var loadJob: Job? = null
    private var countdownJob: Job? = null
    private var loadAttempted = false
    private var autoPlayTrailer = trailerPlaybackSupported
    private var lastIdentity: String? = null
    private var lastInput: PostPlayPlaybackInput? = null

    fun evaluate(input: PostPlayPlaybackInput) {
        if (lastIdentity != null && lastIdentity != input.identity) {
            log.d { "Playback changed: recommendations reset" }
            reset()
        }
        lastIdentity = input.identity
        lastInput = input

        val usable = shouldUsePostPlayRecommendation(
            contentType = input.contentType,
            episodesResolved = input.episodesResolved,
            hasNextEpisode = input.hasNextEpisode,
            nextEpisodeHasAired = input.nextEpisodeHasAired,
            enabled = input.enabled,
        )
        if (!usable || input.hasError || input.nextEpisodeAutoPlayActive) {
            if (uiState.isVisible || uiState.isLoading || uiState.recommendation != null) {
                log.d { "Recommendations not applicable any more (usable=$usable, error=${input.hasError})" }
                reset()
            }
            return
        }
        if (uiState.hasReturnedToPlayer) return
        if (isShortPlaceholderDuration(input.durationMs)) return

        if (!loadAttempted && shouldPrefetchPostPlayRecommendation(
                positionMs = input.positionMs,
                durationMs = input.durationMs,
                progressThreshold = postPlayPrefetchProgress(
                    contentType = input.contentType,
                    movieThresholdPercent = input.movieThresholdPercent,
                    durationMs = input.durationMs,
                    skipIntervals = input.skipIntervals,
                    episodeThresholdMode = input.episodeThresholdMode,
                    episodeThresholdPercent = input.episodeThresholdPercent,
                    episodeThresholdMinutesBeforeEnd = input.episodeThresholdMinutesBeforeEnd,
                ),
            )
        ) {
            load(input)
        }

        if (uiState.recommendation == null) return
        val shouldShow = input.isEnded || shouldShowPostPlayRecommendation(
            contentType = input.contentType,
            positionMs = input.positionMs,
            durationMs = input.durationMs,
            skipIntervals = input.skipIntervals,
            movieThresholdPercent = input.movieThresholdPercent,
            episodeThresholdMode = input.episodeThresholdMode,
            episodeThresholdPercent = input.episodeThresholdPercent,
            episodeThresholdMinutesBeforeEnd = input.episodeThresholdMinutesBeforeEnd,
        )
        if (!shouldShow) {
            // Seeked back before the threshold: the panel can come back later.
            if (uiState.isVisible && !uiState.isTrailerPlaying) {
                log.d { "Seeked before the threshold: panel hidden" }
                cancelCountdown()
                uiState = uiState.copy(isVisible = false)
            }
            return
        }
        if (uiState.isVisible || input.blocked) return

        log.i { "Panel shown for ${input.metaType}/${input.metaId} (${uiState.count} recommendations, ended=${input.isEnded})" }
        uiState = uiState.copy(isVisible = true)
        maybeStartCountdown()
    }

    fun showNext() = select(uiState.index + 1)

    fun showPrevious() = select(uiState.index - 1)

    fun returnToPlayer() {
        if (!uiState.isVisible) return
        log.i { "Back to the player: recommendations closed for this playback" }
        cancelCountdown()
        uiState = uiState.copy(
            isVisible = false,
            hasReturnedToPlayer = true,
            countdownSeconds = null,
            isTrailerPlaying = false,
        )
    }

    fun playTrailer() {
        val trailer = uiState.recommendation?.trailer ?: return
        cancelCountdown()
        log.i { "Trailer started for ${uiState.recommendation?.title} (audio=${trailer.audioUrl != null})" }
        uiState = uiState.copy(isTrailerPlaying = true, countdownSeconds = null)
    }

    fun stopTrailer() {
        if (!uiState.isTrailerPlaying) return
        log.d { "Trailer stopped" }
        uiState = uiState.copy(isTrailerPlaying = false, hasAutoPlayedTrailer = true)
    }

    fun onTrailerEnded() {
        log.d { "Trailer ended" }
        uiState = uiState.copy(isTrailerPlaying = false, hasAutoPlayedTrailer = true, countdownSeconds = null)
    }

    fun onTrailerError() {
        log.w { "Trailer playback failed for ${uiState.recommendation?.title}" }
        val index = uiState.index
        cache[index]?.let { cache[index] = it.copy(trailer = null) }
        uiState = uiState.copy(
            recommendation = uiState.recommendation?.copy(trailer = null),
            isTrailerPlaying = false,
            countdownSeconds = null,
        )
    }

    fun reset() {
        loadJob?.cancel()
        loadJob = null
        detailJobs.values.forEach(Job::cancel)
        detailJobs.clear()
        cancelCountdown()
        candidates = emptyList()
        cache.clear()
        loadAttempted = false
        autoPlayTrailer = trailerPlaybackSupported
        uiState = PostPlayRecommendationUiState()
    }

    private fun load(input: PostPlayPlaybackInput) {
        loadAttempted = true
        loadJob = scopeProvider().launch {
            uiState = uiState.copy(isLoading = true, sourceTitle = input.title)
            log.i { "Loading recommendations for ${input.metaType}/${input.metaId}" }
            val found = try {
                val meta = input.meta ?: MetaDetailsRepository.fetch(type = input.metaType, id = input.metaId)
                meta?.let {
                    MetaDetailsRepository.fetchPostPlayCandidates(
                        meta = it,
                        fallbackItemId = input.metaId,
                        fallbackItemType = input.metaType,
                    )
                }.orEmpty().take(MAX_POST_PLAY_RECOMMENDATIONS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log.e(error) { "Recommendations not loaded for ${input.metaType}/${input.metaId}" }
                emptyList()
            }
            loadJob = null
            if (found.isEmpty()) {
                log.i { "No recommendations for ${input.metaType}/${input.metaId}" }
                uiState = uiState.copy(isLoading = false)
                return@launch
            }
            log.i { "${found.size} recommendations ready for ${input.metaType}/${input.metaId}" }
            candidates = found
            val first = cache.getOrPut(0) { found[0].toRecommendation() }
            uiState = uiState.copy(
                recommendation = first,
                index = 0,
                count = found.size,
                isLoading = false,
            )
            loadDetails(0)
            loadDetails(1)
            lastInput?.let(::evaluate)
        }
    }

    private fun select(target: Int) {
        if (!uiState.isVisible || target !in candidates.indices || target == uiState.index) return
        // A title chosen by hand never starts its trailer by itself.
        autoPlayTrailer = false
        cancelCountdown()
        val recommendation = cache.getOrPut(target) { candidates[target].toRecommendation() }
        log.d { "Recommendation ${target + 1}/${candidates.size}: ${recommendation.title}" }
        uiState = uiState.copy(
            recommendation = recommendation,
            index = target,
            isTrailerPlaying = false,
            countdownSeconds = null,
            isLoadingTrailer = trailerPlaybackSupported && detailJobs[target]?.isCompleted != true,
        )
        loadDetails(target)
        loadDetails(target + 1)
    }

    /** Full meta (logo, backdrop, description, trailer) of a recommendation, loaded once. */
    private fun loadDetails(index: Int) {
        if (index !in candidates.indices || detailJobs.containsKey(index)) return
        val candidate = candidates[index]
        if (index == uiState.index) uiState = uiState.copy(isLoadingTrailer = trailerPlaybackSupported)
        detailJobs[index] = scopeProvider().launch {
            val meta = try {
                MetaDetailsRepository.peek(type = candidate.type, id = candidate.id)
                    ?: MetaDetailsRepository.fetch(type = candidate.type, id = candidate.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log.w(error) { "Details of ${candidate.type}/${candidate.id} not loaded" }
                null
            }
            val trailer = if (trailerPlaybackSupported && meta != null) resolveTrailer(meta) else null
            val base = cache[index] ?: candidate.toRecommendation()
            val updated = base.mergeDetails(meta).copy(trailer = trailer)
            cache[index] = updated
            log.d { "Details ready for ${updated.title} (trailer=${trailer != null})" }
            if (uiState.index == index && uiState.recommendation?.id == updated.id) {
                uiState = uiState.copy(recommendation = updated, isLoadingTrailer = false)
                maybeStartCountdown()
            }
        }
    }

    private suspend fun resolveTrailer(meta: MetaDetails): TrailerPlaybackSource? {
        val trailer = selectHeroTrailer(meta.trailers) ?: return null
        return try {
            TrailerPlaybackResolver.resolveFromYouTubeUrl(trailer.youtubePlaybackUrl())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            log.w(error) { "Trailer of ${meta.type}/${meta.id} not resolved" }
            null
        }
    }

    /** Counts down, then plays the trailer of the first recommendation (once per playback). */
    private fun maybeStartCountdown() {
        val state = uiState
        if (!state.isVisible || !autoPlayTrailer || state.isTrailerPlaying || state.hasAutoPlayedTrailer) return
        if (state.recommendation?.trailer == null || countdownJob != null) return
        log.d { "Trailer countdown started" }
        countdownJob = scopeProvider().launch {
            for (seconds in POST_PLAY_TRAILER_COUNTDOWN_SECONDS downTo 1) {
                uiState = uiState.copy(countdownSeconds = seconds)
                delay(1_000L)
            }
            countdownJob = null
            if (uiState.isVisible) playTrailer()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (uiState.countdownSeconds != null) uiState = uiState.copy(countdownSeconds = null)
    }
}

private fun MetaPreview.toRecommendation(): PostPlayRecommendation = PostPlayRecommendation(
    id = id,
    type = type,
    title = name,
    poster = poster,
    background = banner ?: landscapePoster,
    logo = logo,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    runtime = runtime,
    ageRating = ageRating,
)

private fun PostPlayRecommendation.mergeDetails(meta: MetaDetails?): PostPlayRecommendation {
    if (meta == null) return this
    return copy(
        title = meta.name.ifBlank { title },
        poster = meta.poster ?: poster,
        background = meta.background ?: background,
        logo = meta.logo ?: logo,
        description = meta.description?.takeIf { it.isNotBlank() } ?: description,
        releaseInfo = meta.releaseInfo ?: releaseInfo,
        imdbRating = meta.imdbRating ?: imdbRating,
        genres = meta.genres.ifEmpty { genres },
        runtime = meta.runtime ?: runtime,
        ageRating = meta.ageRating ?: ageRating,
    )
}
