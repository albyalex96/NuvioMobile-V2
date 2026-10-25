package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.party.PartyMedia
import com.nuvio.app.features.player.ExternalPlayerIntentResult
import com.nuvio.app.features.player.ExternalPlayerPlatform
import com.nuvio.app.features.player.PlayerEpisodeChange
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.player.PlayerScreen
import com.nuvio.app.features.streams.StreamLaunch
import com.nuvio.app.features.streams.StreamLaunchStore
import com.nuvio.app.features.watchprogress.ResumePromptRepository
import com.nuvio.app.features.watchprogress.StartedPlaybackPlaceholderPositionMs
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.PlayerRoute
import com.nuvio.app.navigation.StreamRoute

@Composable
internal fun PlayerDestination(
    route: PlayerRoute,
    navController: NuvioNavigator,
    externalPlayerId: String?,
    externalPlayerNotConfiguredText: String,
    externalPlayerFailedText: String,
    onExternalPlayerLaunch: (PlayerLaunch) -> Unit,
    launchExternalPlayer: (ExternalPlayerIntentResult.Success) -> Boolean,
    openExternalStreamUrl: (String) -> Boolean,
    onOpenPartyMedia: (PartyMedia) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val launch = remember(route.launchId) { PlayerLaunchStore.get(route.launchId) }
    if (launch == null) {
        LaunchedEffect(route.launchId) {
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    LaunchedEffect(launch.videoId) {
        launch.videoId?.let { ResumePromptRepository.markPlayerEntered(it) }
    }
    val onExitToDetails = remember(navController, route, launch, onBack) {
        {
            val details = DetailRoute(type = launch.parentMetaType, id = launch.parentMetaId, title = launch.title)
            val handled = launch.parentMetaId.isNotBlank() &&
                navController.popBackToOrReplace(route, details) { it is DetailRoute && it.id == details.id }
            if (!handled) onBack()
        }
    }
    val onEpisodeChanged = remember(navController, route) {
        { change: PlayerEpisodeChange -> replaceStaleStreamRoute(navController, route, change) }
    }
    PlayerScreen(
        profileId = launch.profileId,
        title = launch.title,
        sourceUrl = launch.sourceUrl,
        sourceAudioUrl = launch.sourceAudioUrl,
        sourceHeaders = launch.sourceHeaders,
        sourceResponseHeaders = launch.sourceResponseHeaders,
        externalSubtitles = launch.externalSubtitles,
        streamType = launch.streamType,
        logo = launch.logo,
        poster = launch.poster,
        background = launch.background,
        seasonNumber = launch.seasonNumber,
        episodeNumber = launch.episodeNumber,
        episodeTitle = launch.episodeTitle,
        episodeThumbnail = launch.episodeThumbnail,
        streamTitle = launch.streamTitle,
        streamSubtitle = launch.streamSubtitle,
        initialBingeGroup = launch.bingeGroup,
        pauseDescription = launch.pauseDescription,
        providerName = launch.providerName,
        providerAddonId = launch.providerAddonId,
        contentType = launch.contentType,
        videoId = launch.videoId,
        parentMetaId = launch.parentMetaId,
        parentMetaType = launch.parentMetaType,
        torrentInfoHash = launch.torrentInfoHash,
        torrentFileIdx = launch.torrentFileIdx,
        torrentFilename = launch.torrentFilename,
        torrentTrackers = launch.torrentTrackers,
        initialPositionMs = launch.initialPositionMs,
        initialProgressFraction = launch.initialProgressFraction,
        contentLanguage = launch.contentLanguage,
        launchId = route.launchId,
        sourceFilename = launch.sourceFilename,
        stream = launch.stream,
        onBack = onBack,
        onExitToDetails = onExitToDetails,
        onEpisodeChanged = onEpisodeChanged,
        onOpenInExternalPlayer = { request ->
            val playerLaunch = PlayerLaunch(
                profileId = launch.profileId,
                title = launch.title,
                sourceUrl = request.sourceUrl,
                sourceHeaders = request.sourceHeaders,
                logo = launch.logo,
                poster = launch.poster,
                background = launch.background,
                seasonNumber = launch.seasonNumber,
                episodeNumber = launch.episodeNumber,
                episodeTitle = launch.episodeTitle,
                episodeThumbnail = launch.episodeThumbnail,
                streamTitle = request.streamTitle ?: launch.streamTitle,
                streamSubtitle = launch.streamSubtitle,
                bingeGroup = launch.bingeGroup,
                pauseDescription = launch.pauseDescription,
                providerName = launch.providerName,
                providerAddonId = launch.providerAddonId,
                contentType = launch.contentType,
                videoId = launch.videoId,
                parentMetaId = launch.parentMetaId,
                parentMetaType = launch.parentMetaType,
                initialPositionMs = request.resumePositionMs,
                privateViewing = launch.privateViewing,
            )
            onExternalPlayerLaunch(playerLaunch)
            val intentResult = ExternalPlayerPlatform.buildIntent(
                request = request,
                playerId = externalPlayerId,
            )
            when (intentResult) {
                is ExternalPlayerIntentResult.Success -> {
                    val launched = launchExternalPlayer(intentResult)
                    if (!launched) {
                        NuvioToastController.show(externalPlayerFailedText)
                    } else if (externalPlayerId == "infuse") {
                        onBack()
                    }
                }
                ExternalPlayerIntentResult.NotConfigured -> {
                    NuvioToastController.show(externalPlayerNotConfiguredText)
                }
                ExternalPlayerIntentResult.Failed -> {
                    NuvioToastController.show(externalPlayerFailedText)
                }
            }
        },
        onOpenExternalUrl = { url ->
            openExternalStreamUrl(url)
        },
        onOpenPartyMedia = onOpenPartyMedia,
        modifier = Modifier.fillMaxSize(),
    )
}

private val episodeChangeLog = Logger.withTag("PlayerEpisodeChange")

/**
 * After the player moves to another episode, the sources page left below it still belongs to the
 * launch episode: going back there reopened (and could auto play) that one. It is swapped for the
 * sources of the episode now playing, opened for a manual pick.
 */
private fun replaceStaleStreamRoute(
    navController: NuvioNavigator,
    playerRoute: PlayerRoute,
    change: PlayerEpisodeChange,
) {
    navController.replaceRouteBelow(playerRoute) { below ->
        val streamRoute = below as? StreamRoute ?: return@replaceRouteBelow null
        val streamLaunch = StreamLaunchStore.get(streamRoute.launchId) ?: return@replaceRouteBelow null
        if (streamLaunch.isSameEpisode(change)) return@replaceRouteBelow null
        val progress = WatchProgressRepository.progressForVideo(
            videoId = change.videoId,
            parentMetaId = streamLaunch.parentMetaId,
            seasonNumber = change.seasonNumber,
            episodeNumber = change.episodeNumber,
        )?.takeIf { !it.isEffectivelyCompleted && it.lastPositionMs > StartedPlaybackPlaceholderPositionMs }
        val launchId = StreamLaunchStore.put(
            streamLaunch.copy(
                videoId = change.videoId,
                seasonNumber = change.seasonNumber,
                episodeNumber = change.episodeNumber,
                episodeTitle = change.episodeTitle,
                episodeThumbnail = change.episodeThumbnail,
                pauseDescription = change.pauseDescription,
                resumePositionMs = progress?.lastPositionMs,
                resumeProgressFraction = null,
                manualSelection = true,
                startFromBeginning = false,
            ),
        )
        episodeChangeLog.i {
            "Sources page below the player now on S${change.seasonNumber}E${change.episodeNumber} " +
                "(was S${streamLaunch.seasonNumber}E${streamLaunch.episodeNumber})"
        }
        StreamRoute(launchId = launchId, title = streamRoute.title)
    }
}

private fun StreamLaunch.isSameEpisode(change: PlayerEpisodeChange): Boolean =
    if (seasonNumber != null && episodeNumber != null && change.seasonNumber != null && change.episodeNumber != null) {
        seasonNumber == change.seasonNumber && episodeNumber == change.episodeNumber
    } else {
        videoId == change.videoId
    }
