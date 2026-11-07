package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.postplay.PostPlayContentType
import com.nuvio.app.features.player.postplay.PostPlayPlaybackInput
import com.nuvio.app.features.player.postplay.PostPlayRecommendation
import com.nuvio.app.features.player.postplay.PostPlayRecommendationOverlay
import com.nuvio.app.features.player.postplay.resolvePostPlayContentType

private val log = Logger.withTag("PostPlayRecommendations")

/** Feeds the post-play controller with the playback state and reacts to its panel and trailer. */
@Composable
internal fun PlayerScreenRuntime.BindPostPlayRecommendationEffects() {
    val type = resolvePostPlayContentType(if (isSeries) "series" else contentType ?: parentMetaType)
    val input = PostPlayPlaybackInput(
        identity = "$parentMetaId|$activeVideoId|$activeSeasonNumber|$activeEpisodeNumber",
        contentType = type.takeUnless { isLiveTvPlayback },
        // A watch party follows the host: nobody leaves the shared playback by themselves.
        enabled = playerSettingsUiState.postPlayRecommendationsEnabled && !isPartyControlLocked &&
            args.onOpenRecommendation != null,
        movieThresholdPercent = playerSettingsUiState.postPlayMovieThresholdPercent,
        episodesResolved = type != PostPlayContentType.SERIES || playerMetaVideos.isNotEmpty(),
        hasNextEpisode = nextEpisodeInfo != null,
        nextEpisodeHasAired = nextEpisodeInfo?.hasAired,
        nextEpisodeAutoPlayActive = nextEpisodeAutoPlaySearching || nextEpisodeAutoPlayCountdown != null,
        hasError = errorMessage != null,
        blocked = isScrubbingTimeline || showSourcesPanel || showEpisodesPanel || showChaptersPanel ||
            showLiveChannelsPanel || showSubmitIntroModal || showAudioModal || showSubtitleModal ||
            showVideoSettingsModal || showPartyPanel || showCastPicker || showStillWatchingPrompt ||
            pendingP2pSwitch != null,
        isEnded = playbackSnapshot.isEnded,
        positionMs = playbackSnapshot.positionMs,
        durationMs = playbackSnapshot.durationMs,
        skipIntervals = skipIntervals,
        episodeThresholdMode = playerSettingsUiState.nextEpisodeThresholdMode,
        episodeThresholdPercent = playerSettingsUiState.nextEpisodeThresholdPercent,
        episodeThresholdMinutesBeforeEnd = playerSettingsUiState.nextEpisodeThresholdMinutesBeforeEnd,
        meta = playerMeta,
        metaId = parentMetaId,
        metaType = parentMetaType,
        title = title,
    )
    LaunchedEffect(input) { postPlay.evaluate(input) }

    val visible = postPlay.uiState.isVisible
    LaunchedEffect(visible) {
        // The panel covers the player: its controls step aside while it is open.
        if (visible) controlsVisible = false
    }
    val trailerPlaying = postPlay.uiState.isTrailerPlaying
    LaunchedEffect(trailerPlaying) {
        // The trailer has the sound: the video below pauses (the user resumes it on return).
        if (trailerPlaying && playbackSnapshot.isPlaying) {
            log.d { "Trailer playing: main playback paused" }
            setPlaybackPlaying(false)
        }
    }
}

@Composable
internal fun PlayerScreenRuntime.RenderPostPlayRecommendations() {
    PostPlayRecommendationOverlay(
        state = postPlay.uiState,
        trailerPlaybackSupported = postPlay.trailerPlaybackSupported,
        horizontalSafePadding = horizontalSafePadding,
        onPlay = { openPostPlayRecommendation(it, playNow = true) },
        onOpenDetails = { openPostPlayRecommendation(it, playNow = false) },
        onPlayTrailer = postPlay::playTrailer,
        onStopTrailer = postPlay::stopTrailer,
        onTrailerEnded = postPlay::onTrailerEnded,
        onTrailerError = postPlay::onTrailerError,
        onPrevious = postPlay::showPrevious,
        onNext = postPlay::showNext,
        onReturnToPlayer = {
            postPlay.returnToPlayer()
            controlsVisible = !playerControlsLocked
        },
    )
}

/** Leaves the player for [recommendation]: a movie plays right away, a series opens its details. */
private fun PlayerScreenRuntime.openPostPlayRecommendation(recommendation: PostPlayRecommendation, playNow: Boolean) {
    val open = args.onOpenRecommendation ?: return
    val isMovie = resolvePostPlayContentType(recommendation.type) == PostPlayContentType.MOVIE
    log.i {
        "Recommendation chosen: ${recommendation.type}/${recommendation.id} " +
            "(${if (playNow && isMovie) "play" else "details"})"
    }
    postPlay.stopTrailer()
    flushWatchProgress()
    open(
        PostPlayTarget(
            type = recommendation.type,
            id = recommendation.id,
            title = recommendation.title,
            logo = recommendation.logo,
            poster = recommendation.poster,
            background = recommendation.background,
            playNow = playNow && isMovie,
        ),
    )
}
